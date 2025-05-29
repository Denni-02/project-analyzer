package ml;

import ml.arff.CSVToARFFConverter;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import analyzer.util.Configuration;
import ml.model.ClassifierFactory;
import ml.evaluation.ClassifierEvaluator;
import ml.evaluation.EvaluationResult;


public class MLApp {

    public static void main(String[] args) {

        try {

            // === 1. Conversione da CSV a ARFF ===
            if(Configuration.ML_DEBUG) System.out.println("Converto il file CSV in ARFF...");
            CSVToARFFConverter.main(null);  // usa la tua classe esistente

            // === 2. Caricamento del file ARFF ===
            if(Configuration.ML_DEBUG) System.out.println("Carico il dataset ARFF...");
            DataSource source = new DataSource(Configuration.OUTPUT_ARFF1_PATH);
            Instances data = source.getDataSet();

            // === 3. Impostazione dell'attributo target ===
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1); // ultima colonna = bugginess
            }

            // === 4. Info di controllo ===
            if(Configuration.ML_DEBUG) {
                System.out.println("Dataset caricato correttamente.");
                System.out.println("   - Istanze: " + data.numInstances());
                System.out.println("   - Attributi: " + data.numAttributes());
                System.out.println("   - Classe target: " + data.classAttribute().name());
                System.out.println("   - Valori possibili: " + data.classAttribute());
            }

            if(Configuration.ML_DEBUG) System.out.println("\nValutazione NaiveBayes in corso...");

            Classifier naive = ClassifierFactory.getNaiveBayes();
            EvaluationResult nbResult = ClassifierEvaluator.evaluateClassifier(
                    "NaiveBayes", naive, data, 42, 10, 10
            );

            if(Configuration.ML_DEBUG) System.out.println("Risultato:" + nbResult);

            if(Configuration.ML_DEBUG) System.out.println("\nValutazione IBk in corso...");

            Classifier ibk = ClassifierFactory.getIBk();
            EvaluationResult ibkResult = ClassifierEvaluator.evaluateClassifier(
                    "IBk", ibk, data, 42, 10, 10
            );

            if(Configuration.ML_DEBUG) System.out.println("Risultato:" + ibkResult);

            if(Configuration.ML_DEBUG) System.out.println("\nValutazione Random Forest in corso...");

            Classifier randomForest = ClassifierFactory.getRandomForest();
            EvaluationResult rfResult = ClassifierEvaluator.evaluateClassifier(
                    "RandomForest", randomForest, data, 42, 10, 10
            );

            if(Configuration.ML_DEBUG) System.out.println("Risultato:" + rfResult);

        } catch (Exception e) {
            System.err.println("Errore in MLApp: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
