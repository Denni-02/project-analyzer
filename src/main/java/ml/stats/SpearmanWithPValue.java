package ml.stats;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.distribution.TDistribution;

public class SpearmanWithPValue {

    public static class Result {
        public final double rho;
        public final double pValue;

        public Result(double rho, double pValue) {
            this.rho = rho;
            this.pValue = pValue;
        }
    }

    public static Result compute(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Le serie devono avere la stessa lunghezza.");
        }

        SpearmansCorrelation corr = new SpearmansCorrelation();
        double rho = corr.correlation(x, y);

        int n = x.length;
        double t = rho * Math.sqrt((n - 2.0) / (1 - rho * rho));
        TDistribution tDist = new TDistribution(n - 2);
        double pValue = 2 * (1 - tDist.cumulativeProbability(Math.abs(t)));

        return new Result(rho, pValue);
    }
}
