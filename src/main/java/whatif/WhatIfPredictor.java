package whatif;

import ml.evaluation.ClassifierFactory;
import util.Configuration;
import util.ProjectType;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class WhatIfPredictor {

    public static List<PredictionSummary> runPrediction(String datasetAPath, String bPlusPath, String bPath, String cPath, String outputCsvPath, String projectName) throws Exception {
        // === 1. Carica i dataset ===
        Instances datasetA = loadDataset(datasetAPath);
        Instances datasetBplus = loadDataset(bPlusPath);
        Instances datasetB = loadDataset(bPath);
        Instances datasetC = loadDataset(cPath);

        // === 2. Applica Resample ===
        Resample resample = new Resample();
        resample.setBiasToUniformClass(1.0); // bilanciamento
        resample.setNoReplacement(false);

        if (Configuration.SELECTED_PROJECT == ProjectType.OPENJPA) {
            resample.setSampleSizePercent(30.0); // usa solo il 30% dei dati per addestramento
            Configuration.logger.info("OPENJPA: campionamento del 30% del dataset bilanciato.");
        } else {
            resample.setSampleSizePercent(100.0);
            Configuration.logger.info("BOOKKEEPER: uso del 100% del dataset bilanciato.");
        }

        resample.setInputFormat(datasetA);
        Instances balancedData = Filter.useFilter(datasetA, resample);

        // === 3. Configura RandomForest ===
        RandomForest rf = new RandomForest();

        if (Configuration.SELECTED_PROJECT == ProjectType.OPENJPA) {
            // Parametri via options (incluso minNum con -M)
            String[] options = Utils.splitOptions(
                    "-I 40 -depth 15 -K 0 -S 1 -num-slots 1 -M 50"
            );
            rf.setOptions(options);
            rf.setBagSizePercent(50); // imposta separatamente

            Configuration.logger.info("RandomForest OPENJPA: 40 alberi, maxDepth 15, minNum 50, bagSize 50%, 1 thread.");
        } else {
            rf.setNumIterations(100);
            rf.setNumExecutionSlots(Runtime.getRuntime().availableProcessors());
            Configuration.logger.info("RandomForest BOOKKEEPER: 100 alberi, multithread.");
        }

        // === 4. Addestra ===
        Classifier classifier = rf;
        classifier.buildClassifier(balancedData);

        // === 5. Predizioni ===
        PredictionSummary summaryA = predict("A", datasetA, classifier);
        PredictionSummary summaryBplus = predict("B+", datasetBplus, classifier);
        PredictionSummary summaryB = predict("B", datasetB, classifier);
        PredictionSummary summaryC = predict("C", datasetC, classifier);

        List<PredictionSummary> results = Arrays.asList(summaryA, summaryBplus, summaryB, summaryC);
        exportSummaryToCsv(results, outputCsvPath);

        return results;
    }

    private static Instances loadDataset(String path) throws Exception {
        Instances data = new DataSource(path).getDataSet();
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1); // ultima colonna = Bugginess
        }
        return data;
    }

    private static PredictionSummary predict(String name, Instances data, Classifier model) throws Exception {
        int actualBuggy = 0;
        int predictedBuggy = 0;

        for (Instance instance : data) {
            double actual = instance.classValue();
            double predicted = model.classifyInstance(instance);

            if ((int) actual == 1) actualBuggy++;
            if ((int) predicted == 1) predictedBuggy++;
        }

        Configuration.logger.info("[" + name + "] Real buggy: " + actualBuggy + ", Predicted buggy: " + predictedBuggy);
        return new PredictionSummary(name, actualBuggy, predictedBuggy);
    }

    private static void exportSummaryToCsv(List<PredictionSummary> summaries, String path) {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("Dataset,A,E\n");
            for (PredictionSummary s : summaries) {
                String aValue = s.datasetName.equals("B") ? "" : String.valueOf(s.realBuggy);
                writer.write(s.datasetName + "," + aValue + "," + s.predictedBuggy + "\n");
            }
            Configuration.logger.info("Tabella riassuntiva salvata in: " + path);
        } catch (IOException e) {
            Configuration.logger.severe("Errore durante salvataggio CSV: " + e.getMessage());
        }
    }
}
