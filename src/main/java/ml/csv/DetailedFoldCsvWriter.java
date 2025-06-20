package ml.csv;

import ml.model.EvaluationFoldResult;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

public class DetailedFoldCsvWriter {

    private DetailedFoldCsvWriter(){
        // Prevent instantation
    }

    private static final String OUTPUT_PATH = "csv_output/fold_results.csv";

    public static void writeAll(List<EvaluationFoldResult> results) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(OUTPUT_PATH, true))) {
            for (EvaluationFoldResult r : results) {
                pw.printf("%s,%b,%b,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        r.getClassifierName(), r.isApplyFS(), r.isApplySMOTE(), r.getSeed(), r.getRepeat(), r.getFold(),
                        r.getAccuracy(), r.getPrecision(), r.getRecall(), r.getF1(), r.getAuc(), r.getKappa(), r.getNpofb20());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
