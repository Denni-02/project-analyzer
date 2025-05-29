package ml.evaluation;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

public class ClassifierEvaluator {

    private ClassifierEvaluator(){
        // Prevent instantiation
    }

    public static EvaluationResult evaluateClassifier(String name, Classifier cls, Instances data, int seed, int folds, int repeats) throws Exception {
        Evaluation eval = CrossValidator.evaluate(cls, data, seed, folds, repeats);

        double acc = eval.pctCorrect() / 100.0;
        double precision = eval.weightedPrecision();
        double recall = eval.weightedRecall();
        double f1 = eval.weightedFMeasure();
        double auc = eval.weightedAreaUnderROC();
        double kappa = eval.kappa();

        return new EvaluationResult(name, acc, precision, recall, f1, auc, kappa);
    }
}
