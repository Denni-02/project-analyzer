package ml.evaluation;

import ml.model.EvaluationResult;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.util.Random;

public class CrossValidator {

    private CrossValidator(){
        // Prevent instantiation
    }

    public static Evaluation evaluate(Classifier cls, Instances data, int seed, int folds, int repeats) throws Exception {
        Evaluation evaluation = new Evaluation(data);
        Random rand = new Random(seed);

        for (int i = 0; i < repeats; i++) {
            Instances randData = new Instances(data);
            randData.randomize(rand);
            if (randData.classAttribute().isNominal()) {
                randData.stratify(folds);
            }

            for (int n = 0; n < folds; n++) {
                Instances train = randData.trainCV(folds, n);
                Instances test = randData.testCV(folds, n);

                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train);
                evaluation.evaluateModel(clsCopy, test);
            }
        }
        return evaluation;
    }

    public static EvaluationResult evaluateAndWrap(String name, Classifier cls, Instances data, int seed, int folds, int repeats) throws Exception {
        Evaluation eval = evaluate(cls, data, seed, folds, repeats);
        double[][] cm = eval.confusionMatrix();

        double tp = cm[1][1];
        double tn = cm[0][0];
        double fp = cm[0][1];
        double fn = cm[1][0];

        EvaluationResult result = new EvaluationResult(
                name,
                eval.pctCorrect() / 100.0,
                eval.weightedPrecision(),
                eval.weightedRecall(),
                eval.weightedFMeasure(),
                eval.weightedAreaUnderROC(),
                eval.kappa()
        );

        result.setConfusionMatrix(tp, tn, fp, fn);
        return result;
    }

}
