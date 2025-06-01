package ml.stats;

import ml.arff.CSVToARFFConverter;
import ml.csv.EvaluationCsvWriter;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import util.Configuration;
import ml.evaluation.ClassifierFactory;
import ml.model.EvaluationResult;
import java.util.logging.Level;


public class CrossValidation {

    private static final String RESULT_LABEL = "Risultato:";
    private static final String RESULT_FORMAT = "%s %s";

    private static void evaluateAndLog(String name, Classifier classifier, Instances data) throws Exception {
        if (Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) {
            Configuration.logger.info("\nValutazione " + name + " in corso...");
        }

        weka.classifiers.Evaluation eval = ml.evaluation.CrossValidator.evaluate(classifier, data, 42, 10, 10);

        double[][] cm = eval.confusionMatrix();
        double tp = cm[1][1];
        double tn = cm[0][0];
        double fp = cm[0][1];
        double fn = cm[1][0];

        EvaluationResult result = new EvaluationResult(
                name,
                eval.pctCorrect() / 100.0,
                eval.weightedPrecision(),
                eval.weightedRecall(),
                eval.weightedFMeasure(),
                eval.weightedAreaUnderROC(),
                eval.kappa()
        );

        if (Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) {
            Configuration.logger.info(String.format(RESULT_FORMAT, RESULT_LABEL, result));
            Configuration.logger.info(String.format("Matrice di confusione [%s]:", name));
            Configuration.logger.info(String.format("  True Positives (TP): %.0f", tp));
            Configuration.logger.info(String.format("  True Negatives (TN): %.0f", tn));
            Configuration.logger.info(String.format("  False Positives (FP): %.0f", fp));
            Configuration.logger.info(String.format("  False Negatives (FN): %.0f", fn));
        }

        result.setConfusionMatrix(tp, tn, fp, fn);
        EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);
    }


    public static void main(String[] args) {

        try {

            // === 1. Conversione da CSV a ARFF ===
            if(Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) Configuration.logger.info("Converto il file CSV in ARFF...");
            CSVToARFFConverter.main(null);  // usa la tua classe esistente

            // === 2. Caricamento del file ARFF ===
            if(Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) Configuration.logger.info("Carico il dataset ARFF...");
            DataSource source = new DataSource(Configuration.getOutputArffPath());
            Instances data = source.getDataSet();

            // === 3. Impostazione dell'attributo target ===
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1); // ultima colonna = bugginess
            }

            // === 4. Info di controllo ===
            if(Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) {
                Configuration.logger.info("Dataset caricato correttamente.");
                Configuration.logger.info("   - Istanze: " + data.numInstances());
                Configuration.logger.info("   - Attributi: " + data.numAttributes());
                Configuration.logger.info("   - Classe target: " + data.classAttribute().name());
                Configuration.logger.info("   - Valori possibili: " + data.classAttribute());
            }

            evaluateAndLog("NaiveBayes", ClassifierFactory.getNaiveBayes(), data);
            evaluateAndLog("RandomForest", ClassifierFactory.getRandomForest(), data);
            evaluateAndLog("IBk", ClassifierFactory.getIBk(), data);

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore in MLApp", e);
            e.printStackTrace();
        }
    }
}
