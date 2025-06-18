package ml.evaluation;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrossValidator {

    private CrossValidator(){
        // Prevent instantiation
    }

    //  Esegue una validazione incrociata k-fold ripetuta n volte.
    /*public static Evaluation evaluate(Classifier cls, Instances data, int seed, int folds, int repeats) throws Exception {
        Evaluation evaluation = new Evaluation(data); // inizializza la valutazione
        Random rand = new Random(seed); // randomizzatore controllato

        for (int i = 0; i < repeats; i++) {
            Instances randData = new Instances(data);
            randData.randomize(rand);
            if (randData.classAttribute().isNominal()) {
                randData.stratify(folds); // mantiene bilanciamento classi nei fold
            }

            // Esegui K-fold
            for (int n = 0; n < folds; n++) {
                Instances train = randData.trainCV(folds, n);
                Instances test = randData.testCV(folds, n);

                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train); // addestra
                evaluation.evaluateModel(clsCopy, test); // valuta su test
            }
        }
        return evaluation;
    }

     */

    public static double computeNPofB20(Classifier cls, Instances data) throws Exception {
        // Costruisce un nuovo classificatore su tutti i dati
        Classifier copy = weka.classifiers.AbstractClassifier.makeCopy(cls);
        copy.buildClassifier(data);

        // Trova l’indice della classe “Yes”
        int yesIndex = data.classAttribute().indexOfValue("Yes");
        if (yesIndex == -1) {
            throw new IllegalArgumentException("La classe 'Yes' non è presente tra i valori della variabile target.");
        }

        // Prepara una lista (score, isBuggy)
        List<double[]> scored = new ArrayList<>();
        for (int i = 0; i < data.numInstances(); i++) {
            double[] dist = copy.distributionForInstance(data.instance(i));
            double score = dist[yesIndex];  // probabilità che sia buggy
            double actual = data.instance(i).classValue(); // 1 = Yes, 0 = No (valore numerico)
            scored.add(new double[]{score, actual});
        }

        // Ordina per probabilità discendente
        scored.sort((a, b) -> Double.compare(b[0], a[0]));

        int topN = (int) Math.ceil(data.numInstances() * 0.2); // top 20%
        int foundBuggy = 0;
        int totalBuggy = 0;

        for (int i = 0; i < data.numInstances(); i++) {
            if (scored.get(i)[1] == yesIndex) totalBuggy++;
            if (i < topN && scored.get(i)[1] == yesIndex) foundBuggy++;
        }

        if (totalBuggy == 0) return 0.0;

        return (double) foundBuggy / totalBuggy;
    }

}
