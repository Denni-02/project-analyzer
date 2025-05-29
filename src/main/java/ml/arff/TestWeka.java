package ml.arff; // cambia con il package che hai creato

import analyzer.util.Configuration;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class TestWeka {
    public static void main(String[] args) throws Exception {
        // Percorso al file ARFF (deve esistere già)
        String arffPath = "/home/denni/isw2/project-analyzer/csv_output/bookkeeper_output.arff";
        // Carica il dataset da file
        DataSource source = new DataSource(arffPath);
        Instances data = source.getDataSet();

        // Verifica e stampa quante istanze ci sono
        Configuration.logger.info("Caricato dataset con " + data.numInstances() + " istanze.");
        Configuration.logger.info("Numero di attributi: " + data.numAttributes());

        // (opzionale) stampa il nome della classe target se impostata
        if (data.classIndex() != -1) {
            Configuration.logger.info("Classe target: " + data.classAttribute().name());
        } else {
            Configuration.logger.info("Classe non impostata (classIndex = -1)");
        }
    }
}
