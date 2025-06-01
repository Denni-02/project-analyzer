package ml.stats;

import ml.csv.CorrelationCsvWriter;
import ml.stats.SpearmanWithPValue.Result;
import util.Configuration;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.util.logging.Level;

public class SpearmanCalculator {

    public static void main(String[] args) {
        try {
            // === Caricamento dataset ARFF ===
            DataSource source = new DataSource(Configuration.getOutputArffPath());
            Instances data = source.getDataSet();
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // === Prepara il vettore della bugginess come numerico ===
            double[] bugginess = new double[data.numInstances()];
            for (int i = 0; i < data.numInstances(); i++) {
                bugginess[i] = data.instance(i).stringValue(data.classIndex()).equals("Yes") ? 1.0 : 0.0;
            }

            // === Calcola Spearman e p-value per ogni feature numerica ===
            for (int i = 0; i < data.numAttributes() - 1; i++) {
                Attribute attr = data.attribute(i);
                if (!attr.isNumeric()) continue;

                double[] featureValues = new double[data.numInstances()];
                for (int j = 0; j < data.numInstances(); j++) {
                    featureValues[j] = data.instance(j).value(attr);
                }

                Result result = SpearmanWithPValue.compute(featureValues, bugginess);
                CorrelationCsvWriter.writeCorrelation(attr.name(), result.rho, result.pValue);
            }

            if (Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info("Calcolo Spearman completato: " + Configuration.getCorrelationCsvPath());
            }

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore nel calcolo della correlazione Spearman", e);
        }
    }
}
