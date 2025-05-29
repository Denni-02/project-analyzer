package ml.evaluation;

public class EvaluationResult {

    private double accuracy;
    private double precision;
    private double recall;
    private double f1;
    private double auc;
    private double kappa;
    private String classifierName;

    public EvaluationResult(String name, double accuracy, double precision, double recall, double f1, double auc, double kappa) {
        this.classifierName = name;
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.auc = auc;
        this.kappa = kappa;
    }

    @Override
    public String toString() {
        return String.format("[%s] Acc: %.4f  Prec: %.4f  Rec: %.4f  F1: %.4f  AUC: %.4f  Kappa: %.4f",
                classifierName, accuracy, precision, recall, f1, auc, kappa);
    }
}
