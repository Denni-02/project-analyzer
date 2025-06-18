package ml.csv;

import ml.model.EvaluationFoldResult;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

public class DetailedFoldCsvWriter {

    private static final String OUTPUT_PATH = "csv_output/fold_results.csv";

    public static void writeAll(List<EvaluationFoldResult> results) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(OUTPUT_PATH, true))) {
            for (EvaluationFoldResult r : results) {
                pw.printf("%s,%b,%b,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        r.classifierName, r.applyFS, r.applySMOTE, r.seed, r.repeat, r.fold,
                        r.accuracy, r.precision, r.recall, r.f1, r.auc, r.kappa, r.npofb20);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
