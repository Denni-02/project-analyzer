package ml.evaluation;

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
}
