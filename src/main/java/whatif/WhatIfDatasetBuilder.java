package whatif;

import util.Configuration;
import util.ProjectType;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.DoublePredicate;

/**
 Classe per la costruzione dei sotto-dataset necessari all’analisi What-If.
 Usa sempre la feature "Number of Smells" per costruire i dataset B⁺, B e C.
 */
public class WhatIfDatasetBuilder {

    private final String outputDir;

    public WhatIfDatasetBuilder(String outputDir) {
        this.outputDir = outputDir.endsWith("/") ? outputDir : outputDir + "/";
    }


    /*private static final String RAW_FEATURE_NAME = Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER
            ? "Number of Smells"
            : "NestingDepth";

     */
    private static final String RAW_FEATURE_NAME = "Number of Smells";

    //private static final String OUTPUT_DIR = "whatif/";
    private static final String PROJECT_PREFIX = Configuration.getProjectName().toLowerCase(); // bookkeeper / openjpa

    // Costruisce il dataset B+ (metodi con smells)
    public Instances buildBPlus(Instances datasetA) {
        Configuration.logger.info("Costruzione dataset B⁺: " + RAW_FEATURE_NAME + " > 0");
        return filterAndLog(datasetA, RAW_FEATURE_NAME, v -> v > 0, PROJECT_PREFIX + "_Bplus.csv");
    }

    // ostruisce il dataset C (metodi clean, senza smells)
    public Instances buildC(Instances datasetA) {
        Configuration.logger.info("Costruzione dataset C: " + RAW_FEATURE_NAME + " == 0");
        return filterAndLog(datasetA, RAW_FEATURE_NAME, v -> v == 0, PROJECT_PREFIX + "_C.csv");
    }

    // Costruisce il dataset B (what-if): copia di B⁺ con NumberOfSmells forzato a 0
    public Instances buildB(Instances datasetBPlus) {
        Configuration.logger.info("Costruzione dataset B (what-if): B⁺ con " + RAW_FEATURE_NAME + " = 0");

        Instances cloned = new Instances(datasetBPlus);
        int index = getCleanAttributeIndex(cloned, RAW_FEATURE_NAME);

        for (Instance instance : cloned) {
            instance.setValue(index, 0); // FORZA AFeature A 0
        }

        exportToCsv(cloned, PROJECT_PREFIX + "_B.csv");
        Configuration.logger.info("Esportato dataset B con " + cloned.size() + " istanze.");
        return cloned;
    }

    // Filtra le istanze in base a una predicate sulla feature e le esporta
    private Instances filterAndLog(Instances data, String featureName, DoublePredicate predicate, String exportFile) {
        int featureIndex = getCleanAttributeIndex(data, featureName);

        Instances filtered = new Instances(data, 0);
        for (Instance instance : data) {
            if (predicate.test(instance.value(featureIndex))) {
                filtered.add(instance); // FILTRO
            }
        }

        exportToCsv(filtered, exportFile);
        Configuration.logger.info("Esportato " + exportFile + " con " + filtered.size() + " istanze.");
        return filtered;
    }

    // Esporta un dataset in formato CSV, ripulendo i nomi delle feature
    private void exportToCsv(Instances data, String fileName) {
        try {
            Files.createDirectories(Paths.get(outputDir));

            // Normalizza i nomi degli attributi (senza apici, virgolette, ecc.)
            for (int i = 0; i < data.numAttributes(); i++) {
                Attribute attr = data.attribute(i);
                String clean = attr.name().replaceAll("[‘’“”'\"`]", "").trim();
                if (!attr.name().equals(clean)) {
                    data.renameAttribute(i, clean);
                }
            }

            CSVSaver saver = new CSVSaver();
            saver.setInstances(data);
            saver.setFile(new File(outputDir + fileName));
            saver.setFieldSeparator(","); // puoi cambiare in ";" per compatibilità Excel IT
            saver.writeBatch();

            Configuration.logger.info("Esportato CSV pulito: " + fileName);
        } catch (IOException e) {
            Configuration.logger.severe("Errore durante l'export in CSV: " + e.getMessage());
        }
    }

    // Trova l’indice della feature normalizzando i caratteri speciali
    private int getCleanAttributeIndex(Instances data, String cleanName) {
        for (int i = 0; i < data.numAttributes(); i++) {
            String raw = data.attribute(i).name();
            String normalized = raw.replaceAll("[‘’“”'\"`]", "").trim();
            if (normalized.equalsIgnoreCase(cleanName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Attributo non trovato (o malformato): " + cleanName);
    }
}
