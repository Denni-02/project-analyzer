package ml.model;

public class EvaluationResult {

    private double accuracy;
    private double precision;
    private double recall;
    private double f1;
    private double auc;
    private double kappa;
    private String classifierName;
    private double tp;
    private double tn;
    private double fp;
    private double fn;

    public EvaluationResult(String name, double accuracy, double precision, double recall, double f1, double auc, double kappa) {
        this.classifierName = name;
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.auc = auc;
        this.kappa = kappa;
    }

    public void setConfusionMatrix(double tp, double tn, double fp, double fn) {
        this.tp = tp;
        this.tn = tn;
        this.fp = fp;
        this.fn = fn;
    }

    public double getTp() { return tp; }
    public double getTn() { return tn; }
    public double getFp() { return fp; }
    public double getFn() { return fn; }

    public String getClassifierName() {
        return classifierName;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getF1() {
        return f1;
    }

    public double getAuc() {
        return auc;
    }

    public double getKappa() {
        return kappa;
    }


    @Override
    public String toString() {
        return String.format("[%s] Acc: %.4f  Prec: %.4f  Rec: %.4f  F1: %.4f  AUC: %.4f  Kappa: %.4f",
                classifierName, accuracy, precision, recall, f1, auc, kappa);
    }
}
