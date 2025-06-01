package ml.csv;

import ml.model.EvaluationResult;
import util.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

public class EvaluationCsvWriter {

    private static final String FOLDER = "ml_results";
    private static final String HEADER = "Classifier,Accuracy,Precision,Recall,F1,AUC,Kappa,TP,TN,FP,FN";

    public static void write(String projectName, EvaluationResult result) {
        try {
            // Crea directory se non esiste
            File dir = new File(FOLDER);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, projectName + "_cross_validation_results.csv");
            boolean writeHeader = !file.exists();

            FileWriter fw = new FileWriter(file, true); // append mode

            if (writeHeader) fw.write(HEADER + "\n");

            fw.write(String.format("%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.0f,%.0f,%.0f,%.0f\n",
                    result.getClassifierName(),
                    result.getAccuracy(),
                    result.getPrecision(),
                    result.getRecall(),
                    result.getF1(),
                    result.getAuc(),
                    result.getKappa(),
                    result.getTp(),
                    result.getTn(),
                    result.getFp(),
                    result.getFn()
            ));

            fw.close();

            if (Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info("Risultati salvati su file: " + file.getAbsolutePath());
            }

        } catch (IOException e) {
            Configuration.logger.log(Level.SEVERE, "Errore nella scrittura CSV dei risultati", e);
        }
    }
}