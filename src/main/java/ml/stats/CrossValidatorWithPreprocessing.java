package ml.stats;

import ml.csv.EvaluationCsvWriter;
import ml.evaluation.CrossValidator;
import ml.model.EvaluationResult;
import util.Configuration;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.filters.unsupervised.attribute.Remove;
import weka.core.converters.ConverterUtils.DataSource;
import ml.csv.DetailedFoldCsvWriter;
import ml.model.EvaluationFoldResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrossValidatorWithPreprocessing {

    private CrossValidatorWithPreprocessing() {}

    /**
     * Metodo per eseguire la cross validation di un classificatore con possibili step di preprocessing:
     * - Feature Selection (InfoGain + Ranker)
     * - SMOTE per il bilanciamento della classe minoritaria
     */
    public static EvaluationResult evaluateAndWrap(String name, Classifier cls, Instances data,
                                                   int seed, int folds, int repeats,
                                                   boolean applyFeatureSelection,
                                                   boolean applySmote) throws Exception {

        long startTime = System.currentTimeMillis();
        Configuration.logger.info("Inizio valutazione: " + name);

        // Rimuove l'attributo ReleaseID se presente per evitare data leakage
        int releaseIdIndex = data.attribute("ReleaseID") != null ? data.attribute("ReleaseID").index() : -1;
        if (releaseIdIndex != -1) {
            Remove remove = new Remove();
            remove.setAttributeIndicesArray(new int[]{releaseIdIndex});
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }

        //Evaluation evaluation = new Evaluation(data);
        double totalAccuracy = 0;
        double totalPrecision = 0;
        double totalRecall = 0;
        double totalF1 = 0;
        double totalAUC = 0;
        double totalKappa = 0;
        int totalFolds = folds * repeats;

        Random rand = new Random(seed);

        // 10x10-fold Cross Validation
        for (int i = 0; i < repeats; i++) {
            Configuration.logger.info("Ripetizione " + (i + 1) + "/" + repeats);

            // Shuffle e stratifica
            Instances randData = new Instances(data);
            randData.randomize(rand);
            if (randData.classAttribute().isNominal()) {
                randData.stratify(folds);
            }

            Instances trainFull = new Instances(randData); // usato per FS e SMOTE

            // Applica Feature Selection su tutte le istanze
            if (applyFeatureSelection) {
                RemoveUseless remove = new RemoveUseless();
                remove.setInputFormat(trainFull);
                trainFull = Filter.useFilter(trainFull, remove);

                AttributeSelection selector = new AttributeSelection();
                InfoGainAttributeEval eval = new InfoGainAttributeEval();
                Ranker ranker = new Ranker();
                ranker.setThreshold(0.01); // rimuove feature non informative
                selector.setEvaluator(eval);
                selector.setSearch(ranker);
                selector.SelectAttributes(trainFull);
                trainFull = selector.reduceDimensionality(trainFull);
            }

            // Applica SMOTE una volta per ripetizione
            if (applySmote && Configuration.getProjectName() == "BOOKKEEPER") {
                double percentage = 65;
                /*if (Configuration.SELECTED_PROJECT.toString().equalsIgnoreCase("BOOKKEEPER")) {
                    percentage = 65.0;
                } else if (Configuration.SELECTED_PROJECT.toString().equalsIgnoreCase("OPENJPA")) {
                    percentage = 65.0;
                }

                 */

                SMOTE smote = new SMOTE();
                smote.setPercentage(percentage);
                smote.setNearestNeighbors(5);
                smote.setInputFormat(trainFull);
                trainFull = Filter.useFilter(trainFull, smote);
            }

            List<EvaluationFoldResult> foldResults = new ArrayList<>();

            // Cross Validation manuale su trainFull
            for (int n = 0; n < folds; n++) {
                Configuration.logger.info("  - Fold " + (n + 1) + "/" + folds);

                Instances train = trainFull.trainCV(folds, n);
                Instances test = trainFull.testCV(folds, n);

                // Clona il classificatore e addestra
                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train);
                Evaluation foldEval = new Evaluation(train);
                //evaluation.evaluateModel(clsCopy, test);
                foldEval.evaluateModel(clsCopy, test);
                double npofb20Fold = CrossValidator.computeNPofB20(clsCopy, test);


                EvaluationFoldResult foldResult = new EvaluationFoldResult(
                        name, applyFeatureSelection, applySmote, seed, i, n,
                        foldEval.pctCorrect() / 100.0,
                        foldEval.weightedPrecision(),
                        foldEval.weightedRecall(),
                        foldEval.weightedFMeasure(),
                        foldEval.weightedAreaUnderROC(),
                        foldEval.kappa(),
                        npofb20Fold
                );

                foldResults.add(foldResult);


                totalAccuracy += foldEval.pctCorrect() / 100.0;
                totalPrecision += foldEval.weightedPrecision();
                totalRecall += foldEval.weightedRecall();
                totalF1 += foldEval.weightedFMeasure();
                totalAUC += foldEval.weightedAreaUnderROC();
                totalKappa += foldEval.kappa();
            }

            DetailedFoldCsvWriter.writeAll(foldResults);
        }

        EvaluationResult result = new EvaluationResult(
                name,
                totalAccuracy / totalFolds,
                totalPrecision / totalFolds,
                totalRecall / totalFolds,
                totalF1 / totalFolds,
                totalAUC / totalFolds,
                totalKappa / totalFolds
        );

        // Calcolo NPofB20 sul dataset originale
        double npofb20 = CrossValidator.computeNPofB20(cls, data);
        result.setNpofb20(npofb20);

        long endTime = System.currentTimeMillis();
        Configuration.logger.info("Tempo totale (ms): " + (endTime - startTime));
        return result;
    }

    public static void main(String[] args) {
        try {
            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            String inputPath = "csv_output/" + project + "_output.arff";
            DataSource source = new DataSource(inputPath);
            Instances data = source.getDataSet();
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // Esegui una sola combinazione (modificare a seconda dei test desiderati)
            boolean applyFS = false;
            boolean applySMOTE = true;

            for (Classifier cls : new Classifier[]{
                    ml.evaluation.ClassifierFactory.getNaiveBayes(),
                    ml.evaluation.ClassifierFactory.getRandomForest(),
                    ml.evaluation.ClassifierFactory.getIBk()
            }) {
                String classifierName = cls.getClass().getSimpleName();
                String runName = String.format("%s_FS=%s_SMOTE=%s", classifierName, applyFS, applySMOTE);

                EvaluationResult result = evaluateAndWrap(runName, cls, data, 42, 10, 10, applyFS, applySMOTE);
                Configuration.logger.info("Valutazione completata: " + result);
                EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);
            }

        } catch (Exception e) {
            Configuration.logger.severe("Errore durante la valutazione con preprocessing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
