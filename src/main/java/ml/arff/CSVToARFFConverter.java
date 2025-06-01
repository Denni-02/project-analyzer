/*package ml.arff;

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


 */

package ml.arff;

import util.Configuration;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;

public class CSVToARFFConverter {

    public static void main(String[] args) throws Exception {
        String csvPath = Configuration.getOutputCsvPath();
        String arffPath = Configuration.getOutputArffPath();

        CSVLoader loader = new CSVLoader();
        loader.setOptions(new String[]{"-F", ";"});
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();

        // === Riordina le etichette: {No, Yes} ===
        int classIndex = data.numAttributes() - 1;
        Attribute originalAttr = data.attribute(classIndex);

        if (originalAttr.isNominal()
                && originalAttr.numValues() == 2
                && "Yes".equals(originalAttr.value(0))
                && "No".equals(originalAttr.value(1))) {

            if (Configuration.logger.isLoggable(Level.INFO))
                Configuration.logger.info("Riordino etichette Bugginess: {Yes,No} → {No,Yes}");

            ArrayList<String> reordered = new ArrayList<>();
            reordered.add("No");
            reordered.add("Yes");

            Attribute newAttr = new Attribute(originalAttr.name(), reordered);
            Instances newData = new Instances(data);
            newData.replaceAttributeAt(newAttr, classIndex);

            for (int i = 0; i < newData.numInstances(); i++) {
                String label = data.instance(i).stringValue(classIndex);
                newData.instance(i).setValue(classIndex, label);
            }

            data = newData;
        }

        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(arffPath));
        saver.writeBatch();

        if (Configuration.logger.isLoggable(Level.INFO)) {
            Configuration.logger.info(String.format("Conversione completata: path = %s", arffPath));
        }
    }
}
