package ml;

import ml.arff.CSVToARFFConverter;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import analyzer.util.Configuration;
import ml.model.ClassifierFactory;
import ml.evaluation.ClassifierEvaluator;
import ml.evaluation.EvaluationResult;

import java.util.logging.Level;


public class MLApp {

    private static final String RESULT_LABEL = "Risultato:";
    private static final String RESULT_FORMAT = "%s %s";

    private static void evaluateAndLog(String name, Classifier classifier, Instances data) throws Exception {
        if (Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) {
            Configuration.logger.info("\nValutazione " + name + " in corso...");
        }

        EvaluationResult result = ClassifierEvaluator.evaluateClassifier(
                name, classifier, data, 42, 10, 10
        );

        if (Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) {
            Configuration.logger.info(String.format(RESULT_FORMAT, RESULT_LABEL, result));
        }
    }


    public static void main(String[] args) {

        try {

            // === 1. Conversione da CSV a ARFF ===
            if(Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) Configuration.logger.info("Converto il file CSV in ARFF...");
            CSVToARFFConverter.main(null);  // usa la tua classe esistente

            // === 2. Caricamento del file ARFF ===
            if(Configuration.logger.isLoggable(Level.INFO) && Configuration.ML_DEBUG) Configuration.logger.info("Carico il dataset ARFF...");
            DataSource source = new DataSource(Configuration.OUTPUT_ARFF1_PATH);
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
            evaluateAndLog("IBk", ClassifierFactory.getIBk(), data);
            evaluateAndLog("RandomForest", ClassifierFactory.getRandomForest(), data);

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore in MLApp", e);
            e.printStackTrace();
        }
    }
}
