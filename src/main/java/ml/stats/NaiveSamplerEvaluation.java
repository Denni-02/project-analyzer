package ml.stats;

import ml.csv.EvaluationCsvWriter;
import ml.model.EvaluationResult;
import util.Configuration;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;
import java.util.logging.Level;

/**
 * Valutazione del classificatore Naive su un sottoinsieme temporale (primi 20.000 metodi) del dataset OpenJPA,
 * con opzioni configurabili per Feature Selection (inclusa rimozione di ReleaseID) e SMOTE.
 */
public class NaiveSamplerEvaluation {

    private static final int SAMPLE_SIZE = 20000;

    public static void main(String[] args) {
        try {
            boolean applyFeatureSelection = false;
            boolean applySmote = true;

            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            if (!project.equals("openjpa")) {
                Configuration.logger.severe("Questa classe è progettata solo per OpenJPA.");
                return;
            }

            String inputPath = "csv_output/" + project + "_output.arff";
            DataSource source = new DataSource(inputPath);
            Instances data = source.getDataSet();
            if (data.classIndex() == -1)
                data.setClassIndex(data.numAttributes() - 1);

            int sampleSize = Math.min(SAMPLE_SIZE, data.numInstances());
            Instances sample = new Instances(data, 0, sampleSize);
            Configuration.logger.info("Dataset campionato temporalmente (" + sample.numInstances() + " istanze)");

            if (applySmote) {
                // === Sampling bilanciato manuale ===
                Instances positives = new Instances(sample, 0);
                Instances negatives = new Instances(sample, 0);

                for (int i = 0; i < sample.numInstances(); i++) {
                    Instance instance = sample.instance(i);
                    if ((int) instance.classValue() == 1) {
                        positives.add(instance);
                    } else {
                        negatives.add(instance);
                    }
                }

                Random rand = new Random(42);
                negatives.randomize(rand);

                int limit = Math.min(positives.numInstances(), negatives.numInstances());
                Instances balanced = new Instances(positives);
                for (int i = 0; i < limit; i++) {
                    balanced.add(negatives.instance(i));
                }
            }

            String runName = String.format("NaiveBayes_FS=%s_SMOTE=%s", applyFeatureSelection, applySmote);

            NaiveBayes naiveBayes = new NaiveBayes();

            // Intestazione CSV fold-wise se necessario
            try (PrintWriter pw = new PrintWriter(new FileWriter("csv_output/fold_results_openjpa.csv"))) {
                pw.println("Classifier,FS,SMOTE,Seed,Repeat,Fold,Accuracy,Precision,Recall,F1,AUC,Kappa,NPofB20");
            }

            EvaluationResult result = CrossValidatorWithPreprocessing.evaluateAndWrap(
                    runName, naiveBayes, sample, 42, 10, 10, applyFeatureSelection, applySmote
            );

            Configuration.logger.info("Valutazione completata: " + result);
            EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore durante la valutazione IBk campionata temporalmente", e);
        }
    }
}