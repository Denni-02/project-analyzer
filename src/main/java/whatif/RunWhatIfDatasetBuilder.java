package whatif;

import util.Configuration;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class RunWhatIfDatasetBuilder {

    public static void main(String[] args) {
        try {
            Configuration.logger.info("== INIZIO: Costruzione sotto-dataset per analisi What-If ==");

            // Carica il dataset A (dal path corretto in base al progetto)
            String arffPath = Configuration.getOutputArffPath();
            Configuration.logger.info("Caricamento dataset A da: " + arffPath);
            Instances datasetA = new DataSource(arffPath).getDataSet();


            // Imposta l'attributo target se necessario
            if (datasetA.classIndex() == -1) {
                datasetA.setClassIndex(datasetA.numAttributes() - 1); // assumiamo ultima colonna = bugginess
            }

            // Costruisci B+, C, B
            WhatIfDatasetBuilder builder = new WhatIfDatasetBuilder("whatif/");
            Instances bPlus = builder.buildBPlus(datasetA);
            Instances c = builder.buildC(datasetA);
            Instances b = builder.buildB(bPlus);

            Configuration.logger.info("== FINE: Costruzione completata ==");
        } catch (Exception e) {
            Configuration.logger.severe("Errore durante la costruzione dei dataset What-If: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
