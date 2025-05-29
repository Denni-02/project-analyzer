package ml.arff; // cambia con il package che hai creato

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
        System.out.println("Caricato dataset con " + data.numInstances() + " istanze.");
        System.out.println("Numero di attributi: " + data.numAttributes());

        // (opzionale) stampa il nome della classe target se impostata
        if (data.classIndex() != -1) {
            System.out.println("Classe target: " + data.classAttribute().name());
        } else {
            System.out.println("Classe non impostata (classIndex = -1)");
        }
    }
}
