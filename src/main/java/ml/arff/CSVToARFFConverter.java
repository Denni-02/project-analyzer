package ml.arff;

import util.Configuration;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;

import java.io.File;
import java.util.logging.Level;

public class CSVToARFFConverter {

    public static void main(String[] args) throws Exception {
        // Percorso assoluto al file CSV
        String csvPath = Configuration.getOutputCsvPath();

        // Percorso dove salvare il file ARFF
        String arffPath = Configuration.getOutputArffPath();

        // Loader WEKA per CSV
        CSVLoader loader = new CSVLoader();
        loader.setOptions(new String[]{"-F", ";"});
        loader.setSource(new File(csvPath));           // imposta il file CSV da leggere
        Instances data = loader.getDataSet();           // carica i dati in memoria

        // Saver WEKA per ARFF
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);                       // passa il dataset da salvare
        saver.setFile(new File(arffPath));              // imposta il file di output
        saver.writeBatch();                             // esegue il salvataggio

        if(Configuration.logger.isLoggable(Level.INFO)) Configuration.logger.info(String.format("Conversione completata: path = %s", arffPath));
    }
}
