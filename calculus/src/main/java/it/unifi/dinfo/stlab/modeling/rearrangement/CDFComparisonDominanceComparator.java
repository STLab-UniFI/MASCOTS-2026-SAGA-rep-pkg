package it.unifi.dinfo.stlab.modeling.rearrangement;

public class CDFComparisonDominanceComparator {


    public boolean isMatched(double[] cdf1, double step1, double[] cdf2, double step2) {
        return this.dominance(cdf1, step1, cdf2, step2) > 0.5;
    }

    public boolean isMatchedAtLeastByEpsilon(double[] cdf1, double step1, double[] cdf2, double step2, double epsilon) {
        return (this.dominance(cdf1,step1, cdf2, step2) - epsilon) > 0.5;
    }

    public double dominance(double[] cdf1, double step1, double[] cdf2, double step2) {
        if (cdf1.length == 0 || cdf2.length == 0) return 0.0;

        double maxTime1 = (cdf1.length - 1) * step1;
        double maxTime2 = (cdf2.length - 1) * step2;
        double maxTime  = Math.max(maxTime1, maxTime2);
        double dt       = Math.min(step1, step2);
        int numPoints   = (int) Math.round(maxTime / dt) + 1;

        double integral = 0.0;
        double prevF1   = interpolate(cdf1, step1, 0.0);

        for (int i = 1; i < numPoints; i++) {
            double t      = i * dt;
            double f1Curr = interpolate(cdf1, step1, t);
            double dF1    = f1Curr - prevF1;
            double tMid   = t - dt / 2.0; // midrule
            double surv2  = 1.0 - interpolate(cdf2, step2, tMid);
            integral     += surv2 * dF1;
            prevF1        = f1Curr;
        }
        return integral;
    }

    private double interpolate(double[] cdf, double step, double t) {
        if (cdf.length == 0) return 0.0;
        if (t <= 0) return cdf[0];
        double maxT = (cdf.length - 1) * step;
        if (t >= maxT) return cdf[cdf.length - 1];

        double exactIdx = t / step;
        int lo = (int) exactIdx;
        int hi = Math.min(lo + 1, cdf.length - 1);
        double frac = exactIdx - lo;

        return cdf[lo] + frac * (cdf[hi] - cdf[lo]);
    }
}
