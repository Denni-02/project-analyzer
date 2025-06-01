package whatif;

import util.Configuration;

import java.util.List;

public class RunWhatIfPredictor {
    public static void main(String[] args) {

        try {
            Configuration.logger.info("== INIZIO: Predizione What-If ==");

            String project = Configuration.getProjectName().toLowerCase();
            String datasetAPath = Configuration.getOutputArffPath();
            String datasetBplusPath = "whatif/" + project + "_Bplus.csv";
            String datasetBPath = "whatif/" + project + "_B.csv";
            String datasetCPath = "whatif/" + project + "_C.csv";
            String outputSummaryCsv = "whatif/" + project + "_summary.csv";

            List<PredictionSummary> results = WhatIfPredictor.runPrediction(
                    datasetAPath,
                    datasetBplusPath,
                    datasetBPath,
                    datasetCPath,
                    outputSummaryCsv,
                    Configuration.getProjectName()
            );

            Configuration.logger.info("== FINE: Predizione completata ==");
        } catch (Exception e) {
            Configuration.logger.severe("Errore nella predizione What-If: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
