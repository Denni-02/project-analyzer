package ml.stats;

import ml.csv.EvaluationCsvWriter;
import ml.evaluation.CrossValidator;
import ml.model.EvaluationResult;
import util.Configuration;
import weka.classifiers.lazy.IBk;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.util.Random;
import java.util.logging.Level;

public class IBkSamplerEvaluation {

    private static final int SAMPLE_SIZE = 30000;  // modifica se vuoi più o meno righe
    private static final int SEED = 42;

    public static void main(String[] args) {
        try {
            // === Carica dataset completo ===
            DataSource source = new DataSource(Configuration.getOutputArffPath());
            Instances data = source.getDataSet();
            if (data.classIndex() == -1)
                data.setClassIndex(data.numAttributes() - 1);

            // === Esegui campionamento casuale ===
            data.randomize(new Random(SEED));
            Instances sample = new Instances(data, 0, Math.min(SAMPLE_SIZE, data.numInstances()));

            Configuration.logger.info("Dataset campionato (" + sample.numInstances() + " istanze)");

            // === Valutazione IBk ===
            IBk ibk = new IBk(3);
            EvaluationResult result = CrossValidator.evaluateAndWrap("IBk", ibk, sample, SEED, 10, 10);

            // === Scrivi CSV con risultati ===
            EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore durante la valutazione IBk campionata", e);
        }
    }
}
