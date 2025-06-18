package ml.model;

public class EvaluationFoldResult {
    public String classifierName;
    public boolean applyFS;
    public boolean applySMOTE;
    public int seed;
    public int repeat;
    public int fold;
    public double accuracy;
    public double precision;
    public double recall;
    public double f1;
    public double auc;
    public double kappa;
    public double npofb20;


    public EvaluationFoldResult(String classifierName, boolean applyFS, boolean applySMOTE,
                                int seed, int repeat, int fold,
                                double accuracy, double precision, double recall,
                                double f1, double auc, double kappa, double npofb20) {
        this.classifierName = classifierName;
        this.applyFS = applyFS;
        this.applySMOTE = applySMOTE;
        this.seed = seed;
        this.repeat = repeat;
        this.fold = fold;
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.auc = auc;
        this.kappa = kappa;
        this.npofb20 = npofb20;
    }

}
