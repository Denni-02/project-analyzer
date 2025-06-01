package whatif;

import ml.stats.SpearmanWithPValue;
import ml.stats.SpearmanWithPValue.Result;
import util.Configuration;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.util.List;

public class WhatIfTheoreticalTest {

    public static void main(String[] args) {
        try {
            Configuration.logger.info("== INIZIO: Esperimento What-If Teorico ==");

            // === 1. Carica il dataset originale ===
            String inputPath = Configuration.getOutputArffPath();
            Instances data = new DataSource(inputPath).getDataSet();
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // === 2. Rimuovi StmtAdded, StmtDeleted e Churn ===
            Remove remove = new Remove();
            //String[] options = new String[]{"-R", getAttributeIndices(data,
                  //  "StmtAdded", "StmtDeleted", "Churn", "MethodHistories","DistinctAuthors", "ReleaseID")};

            String[] options = new String[]{"-R", getAttributeIndices(data,
                    "StmtAdded", "StmtDeleted", "DistinctAuthors", "ReleaseID")};



            remove.setOptions(options);
            remove.setInputFormat(data);
            Instances reduced = Filter.useFilter(data, remove);

            // === 3. Calcola Spearman per tutte le feature numeriche (vs. Bugginess) ===
            int classIndex = reduced.classIndex();
            double[] bugginess = new double[reduced.size()];
            for (int i = 0; i < reduced.size(); i++) {
                bugginess[i] = reduced.instance(i).stringValue(classIndex).equals("Yes") ? 1.0 : 0.0;
            }

            Configuration.logger.info("== Correlazioni Spearman con Bugginess ==");
            for (int i = 0; i < reduced.numAttributes() - 1; i++) {
                Attribute attr = reduced.attribute(i);
                if (!attr.isNumeric()) continue;

                double[] values = new double[reduced.size()];
                for (int j = 0; j < reduced.size(); j++) {
                    values[j] = reduced.instance(j).value(attr);
                }

                Result r = SpearmanWithPValue.compute(values, bugginess);
                Configuration.logger.info(String.format(" - %-25s  rho = %6.4f   p = %.6f", attr.name(), r.rho, r.pValue));
            }


            // === 4. Salva il nuovo ARFF ===
            String reducedPath = "experiments/openjpa_no_stmt.arff";
            ArffSaver saver = new ArffSaver();
            saver.setInstances(reduced);
            saver.setFile(new File(reducedPath));
            saver.writeBatch();
            Configuration.logger.info("Dataset senza StmtAdded/StmtDeleted/Churn salvato in: " + reducedPath);

            // === 5. Costruisci i sotto-dataset con path personalizzato ===
            WhatIfDatasetBuilder builder = new WhatIfDatasetBuilder("experiments/");
            Instances bPlus = builder.buildBPlus(reduced);
            Instances c = builder.buildC(reduced);
            Instances b = builder.buildB(bPlus);

            // === 6. Esegui la predizione e salva i risultati nella cartella experiments ===
            String summaryCsv = "experiments/openjpa_summary_no_stmt.csv";
            String datasetBplusPath = "experiments/openjpa_Bplus.csv";
            String datasetBPath = "experiments/openjpa_B.csv";
            String datasetCPath = "experiments/openjpa_C.csv";

            List<PredictionSummary> results = WhatIfPredictor.runPrediction(
                    reducedPath,
                    datasetBplusPath,
                    datasetBPath,
                    datasetCPath,
                    summaryCsv,
                    Configuration.getProjectName()
            );



            Configuration.logger.info("Esperimento completato. Risultati in: " + summaryCsv);

        } catch (Exception e) {
            Configuration.logger.severe("Errore nell'esperimento What-If teorico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getAttributeIndices(Instances data, String... names) {
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            int index = data.attribute(name).index() + 1;
            if (builder.length() > 0) builder.append(",");
            builder.append(index);
        }
        return builder.toString();
    }

    private static int getAttributeIndex(Instances data, String name) {
        for (int i = 0; i < data.numAttributes(); i++) {
            if (data.attribute(i).name().replaceAll("[‘’“”'\"`]", "").trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Attributo non trovato: " + name);
    }
}
