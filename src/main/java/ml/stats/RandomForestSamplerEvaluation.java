package ml.stats;

import ml.csv.EvaluationCsvWriter;
import ml.model.EvaluationResult;
import util.Configuration;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;
import java.util.logging.Level;

/**
 * Valutazione del classificatore RandomForest su un sottoinsieme temporale del dataset OpenJPA,
 * con configurazioni opzionali di Feature Selection e SMOTE. Stampa tutte le metriche per fold.
 */
public class RandomForestSamplerEvaluation {

    private static final int SAMPLE_SIZE = 15000;
    private static final int SEED = 42;

    public static void main(String[] args) {
        try {
            boolean applyFeatureSelection = false;
            boolean applySmote = true;

            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            if (!project.equals("openjpa")) {
                Configuration.logger.severe("Questa classe è progettata solo per il progetto OpenJPA.");
                return;
            }

            // Caricamento dataset
            String reducedPath = "csv_output/" + project + "_output.arff";
            DataSource source = new DataSource(reducedPath);
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

            // Configura RandomForest leggero
            RandomForest rf = new RandomForest();
            String[] options = Utils.splitOptions("-I 30 -depth 12 -M 50 -K 0 -S 1 -num-slots 1");
            rf.setOptions(options);
            rf.setBagSizePercent(40);
            Configuration.logger.info("RandomForest configurato con 30 alberi, profondità max 12, 1 thread");

            String runName = String.format("RandomForest_FS=%s_SMOTE=%s", applyFeatureSelection, applySmote);

            // Creazione intestazione CSV fold_results se necessario
            try (PrintWriter pw = new PrintWriter(new FileWriter("csv_output/fold_results_openjpa.csv"))) {
                pw.println("Classifier,FS,SMOTE,Seed,Repeat,Fold,Accuracy,Precision,Recall,F1,AUC,Kappa,NPofB20");
            }

            EvaluationResult result = CrossValidatorWithPreprocessing.evaluateAndWrap(
                    runName, rf, sample, SEED, 10, 10, applyFeatureSelection, applySmote
            );

            Configuration.logger.info("Valutazione completata: " + result);
            EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore durante la valutazione RandomForest con sampling OpenJPA", e);
            e.printStackTrace();
        }
    }
}
