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
    private double npofb20;

    public EvaluationResult(String name, double accuracy, double precision, double recall, double f1, double auc, double kappa) {
        this.classifierName = name;
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.auc = auc;
        this.kappa = kappa;
    }

    public double getTp() { return tp; }
    public double getTn() { return tn; }
    public double getFp() { return fp; }
    public double getFn() { return fn; }

    public String getClassifierName() {
        return classifierName;
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

    public double getNpofb20() {
        return npofb20;
    }

    public void setNpofb20(double npofb20) {
        this.npofb20 = npofb20;
    }


    @Override
    public String toString() {
        return String.format("[%s] Acc: %.4f  Prec: %.4f  Rec: %.4f  F1: %.4f  AUC: %.4f  Kappa: %.4f  NPofB20=%.3f",
                classifierName, accuracy, precision, recall, f1, auc, kappa, npofb20);

    }


}
