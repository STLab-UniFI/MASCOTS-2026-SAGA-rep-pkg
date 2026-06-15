package it.unifi.dinfo.stlab.modeling;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

import org.oristool.eulero.evaluation.approximator.TruncatedExponentialMixtureApproximation;
import org.oristool.eulero.evaluation.heuristics.AnalysisHeuristicsVisitor;
import org.oristool.eulero.evaluation.heuristics.SDFHeuristicsVisitor;
import org.oristool.eulero.modeling.Activity;
import org.oristool.eulero.modeling.Simple;
import org.oristool.eulero.modeling.stochastictime.ErlangTime;

import it.unifi.dinfo.stlab.modeling.compositeTaskType.CompositeTaskType;

/**
 * Utility class containing static methods for time limit and time tick
 * calculations
 * used in analysis operations.
 */
public class AnalysisUtils {
    private static double INITIAL_TIME_LIMIT = 1.;

    /**
     * Calculates a time limit and time tick assuming to have a fixed array length
     * for the final cdf.
     * It calculates a first analysis with a fixed granularity and then calculates a
     * new time limit.
     * The time limit is calculates using an epsilon value defined in
     * {@link Constants} class.
     * Then the new time tick is calculated by dividing the time limit by the array
     * length of the cdf.
     * 
     * @param activity    activity
     * @param arrayLength desired arraylength of the cdf
     * @return {@code double[2]}: [limit, tick]
     */
    public static double[] getTimeLimitAndTimeTick(CompositeTaskType taskType, Activity activity, int arrayLength) {
        double[] timeLimitAndTimeTick = AnalysisUtils.calculateTimeLimitAndTimeTickConsideringEdgeCase(activity,
                activity.getFairTimeLimit());
        double timeLimit = timeLimitAndTimeTick[0];
        double timeTick = timeLimitAndTimeTick[1];

        double[] analysis = taskType.analyze(activity, timeLimit, timeTick);

        int index = 0;
        double updatedTimeLimit;
        double updatedTimeTick;

        boolean indexNotFound = true;
        /*
         * The CDF epsilon threshold may not be reached within the initial
         * time limit (from getFairTimeLimit()). When the last CDF sample is
         * still below (1 − epsilon), the time limit is extended by 10% and
         * the analysis is re-run. This ensures that the returned time limit
         * always covers the CDF up to the required precision, avoiding
         * truncation errors in composite analyses.
         */
        while (indexNotFound) {
            for (index = 0; index < analysis.length; index++) {
                if (analysis[index] >= 1 - Constants.EPSILON_FOR_ANALYSIS) {
                    indexNotFound = false;
                    break;
                }
            }
            if (indexNotFound) {
                timeLimit += timeLimit / 10;
                timeTick = timeLimit / Constants.N_ELEMENTS_CDF_CALCULUS;
                analysis = taskType.analyze(activity, timeLimit, timeTick);
            }
        }

        updatedTimeLimit = index * timeTick;
        updatedTimeTick = updatedTimeLimit / (double) arrayLength;

        return new double[] { updatedTimeLimit, updatedTimeTick };
    }

    /**
     * Same as
     * {@link #getTimeLimitAndTimeTick(CompositeTaskType taskType, Activity activity, int arrayLength)}
     * but this is used
     * by {@link SimpleTask}
     * 
     * @param activity    activity
     * @param arrayLength desired arraylength of the cdf
     * @return {@code double[2]}: [limit, tick]
     */
    public static double[] getTimeLimitAndTimeTick(Activity activity, int arrayLength) {
        double[] timeLimitAndTimeTick = AnalysisUtils.calculateTimeLimitAndTimeTickConsideringEdgeCase(activity,
                activity.getFairTimeLimit());
        double timeLimit = timeLimitAndTimeTick[0];
        double timeTick = timeLimitAndTimeTick[1];

        AnalysisHeuristicsVisitor analyzer = new SDFHeuristicsVisitor(BigInteger.valueOf(2), BigInteger.valueOf(5),
                new TruncatedExponentialMixtureApproximation());

        double[] analysis = activity.analyze(new BigDecimal(timeLimit + timeTick), new BigDecimal(timeTick), analyzer);

        if (Double.isNaN(analysis[analysis.length-1]))
            throw new RuntimeException("The analysis reach numbers greater than 1 -> " + analysis[analysis.length-1]);

        int index = 0;
        double updatedTimeLimit;
        double updatedTimeTick;

        boolean indexNotFound = true;

        while (indexNotFound) {
            for (index = 0; index < analysis.length; index++) {
                if (analysis[index] >= 1 - Constants.EPSILON_FOR_ANALYSIS) {
                    indexNotFound = false;
                    break;
                }
            }
            if (indexNotFound) {
                timeLimit += timeLimit / 10;
                timeTick = timeLimit / Constants.N_ELEMENTS_CDF_CALCULUS;
                System.out.println("Rifaccio analisi per ricerca time limit (time limit = " + timeLimit + ", time tick = " + timeTick + ")");
                analysis = activity.analyze(new BigDecimal(timeLimit), new BigDecimal(timeTick), analyzer);
            }
        }

        updatedTimeLimit = index * timeTick + timeTick ;
        updatedTimeTick = updatedTimeLimit / (double) arrayLength;

        return new double[] { updatedTimeLimit, updatedTimeTick };
    }

    /**
     * It calculates time tick based on the time limit.
     * <p>
     * For edge cases where {@code timeLimit == 0} a minimun time tick is used and
     * the timeLimit is updated:
     * <li>{@code timeTick = Double.MIN_NORMAL}</li>
     * <li>{@code timeLimit = Double.MIN_NORMAL * Constants.N_ELEMENTS_CDF_CALCULUS}
     * </li>
     * 
     * @param timeLimit initial guess for timeLimit. Usually get from
     *                  {@link Activity#getFairTimeLimit()}
     * @return {@code double[2]}: [newTimeLimit, newTimeTick]
     */
    private static double[] calculateTimeLimitAndTimeTickConsideringEdgeCase(Activity activity, double timeLimit) {
        // Workaround for Eulero's ErlangTime returning excessively huge or NaN time
        // limits
        if (activity instanceof Simple simpleActivity) {
            if (simpleActivity.getPdf() instanceof ErlangTime erlang) {
                double mean = erlang.getK() / erlang.getRate();
                double stdDev = Math.sqrt(erlang.getK()) / erlang.getRate();
                timeLimit = mean + 10 * stdDev;
            }
        }

        double newTimeLimit = timeLimit;
        double newTimeTick = BigDecimal.valueOf(timeLimit).divide(BigDecimal.valueOf(Constants.N_ELEMENTS_CDF_CALCULUS), 8, RoundingMode.HALF_UP).doubleValue();
//        double newTimeTick = timeLimit / Constants.N_ELEMENTS_CDF_CALCULUS;

        if (newTimeLimit == 0.) {
            newTimeTick = Double.MIN_NORMAL;
            newTimeLimit = newTimeTick * Constants.N_ELEMENTS_CDF_CALCULUS;
        } else if (Double.isNaN(newTimeLimit) || Double.isInfinite(newTimeLimit)) {
            return AnalysisUtils.calculateTimeLimitAndTimeTickWithoutSupport(activity);
        }

        return new double[] { newTimeLimit, newTimeTick };
    }

    private static double[] calculateTimeLimitAndTimeTickWithoutSupport(Activity activity) {
        AnalysisHeuristicsVisitor analyzer = new SDFHeuristicsVisitor(BigInteger.valueOf(2), BigInteger.valueOf(5),
                new TruncatedExponentialMixtureApproximation());

        double timeLimit = INITIAL_TIME_LIMIT;
        double timeStep = timeLimit / ((double) Constants.N_ELEMENTS_CDF_CALCULUS);
        double[] cdf = activity.analyze(new BigDecimal(timeLimit), new BigDecimal(timeStep), analyzer);

        while (cdf[cdf.length - 1] < 1 - Constants.EPSILON_FOR_ANALYSIS) {
            timeLimit *= 2;
            timeStep = timeLimit / ((double) Constants.N_ELEMENTS_CDF_CALCULUS);
            cdf = activity.analyze(new BigDecimal(timeLimit), new BigDecimal(timeStep), analyzer);
        }

        return new double[] { timeLimit, timeStep };
    }
}