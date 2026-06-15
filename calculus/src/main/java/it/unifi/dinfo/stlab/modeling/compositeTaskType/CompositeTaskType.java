package it.unifi.dinfo.stlab.modeling.compositeTaskType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.oristool.eulero.evaluation.approximator.TruncatedExponentialMixtureApproximation;
import org.oristool.eulero.evaluation.heuristics.AnalysisHeuristicsVisitor;
import org.oristool.eulero.evaluation.heuristics.SDFHeuristicsVisitor;
import org.oristool.eulero.modeling.Activity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.AnalysisType;
import it.unifi.dinfo.stlab.modeling.AnalysisUtils;
import it.unifi.dinfo.stlab.modeling.CompositeTask;
import it.unifi.dinfo.stlab.modeling.Constants;
import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.modeling.utils.FailCombination;

/**
 * Abstract base class implementing the Strategy pattern for SAGA task
 * composition operators: SEQ (sequential), AND (parallel fork-join),
 * and XOR (exclusive choice).
 *
 * <p>
 * Each concrete subclass ({@link SeqTaskType}, {@link AndTaskType},
 * {@link XorTaskType}) defines how children are composed into Eulero
 * {@link Activity} graphs for CDF analysis, how compensation activities
 * are structured after a failure, and how failure probabilities are
 * propagated through the workflow tree.
 *
 * <h3>Activity Composition</h3>
 * <p>
 * The {@code compose*Activity} methods build Eulero {@link Activity} trees
 * using {@link org.oristool.eulero.modeling.ModelFactory}: sequences for SEQ,
 * fork-joins for AND, and probabilistic XOR branches. These trees are then
 * analyzed by the Eulero engine's
 * {@link org.oristool.eulero.evaluation.heuristics.SDFHeuristicsVisitor} to
 * compute CDFs.
 *
 * <h3>Failure and Compensation</h3>
 * <p>
 * Compensation logic respects the structural semantics of each operator:
 * <ul>
 *   <li><b>SEQ:</b> only tasks before the failure point are compensated,
 *       in reverse execution order.</li>
 *   <li><b>AND:</b> all parallel branches are compensated; the failing
 *       branch receives partial compensation, sibling branches are fully
 *       compensated in parallel.</li>
 *   <li><b>XOR:</b> only the chosen branch is compensated.</li>
 * </ul>
 *
 * @see CompositeTask
 * @see SeqTaskType
 * @see AndTaskType
 * @see XorTaskType
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public abstract class CompositeTaskType implements Cloneable {
    @JsonBackReference
    private CompositeTask activity;

    /** The child tasks under this composition operator. */
    protected List<SagaTask> children;

    /** Time step used in the most recent analysis. */
    private double lastTimeTick;

    /** Returns the type discriminator for this composition operator. */
    @JsonProperty
    public abstract TaskEnumType getEnumType();

    /**
     * Composes only the scalable component activity for all children.
     *
     * @return the composed scalable activity
     */
    public abstract Activity composeForwardActivity();

    /**
     * Composes the failure-aware activity for STPN modeling in AND-blocks.
     * Each child's activity includes probabilistic success/failure branching.
     *
     * @param workflowServiceNames all service names for enabling function generation
     * @return the composed failure-aware activity
     */
    public abstract Activity composeActivityWithFailure(List<String> workflowServiceNames);

    /**
     * V2: Computes the happy path probability 
     *
     * @return the happy path probability
     */
    public abstract double getHappyPathProbability();

    /**
     * Calculates the concurrency groups of the subtree
     * @return a list containing all the concurrency groups
     */
    public abstract List<List<SimpleTask>> getConcurrencyGroups();

    public Analysis analyze(AnalysisType analysisType, double timeLimit, double timeStep) {
        Activity activity = this.getActivityFromAnalysisType(analysisType);
        return new Analysis(this.analyze(activity, timeLimit, timeStep), timeLimit, timeStep);
    }

    public Analysis analyze(AnalysisType analysisType, int cdfLength) {
        Activity activity = this.getActivityFromAnalysisType(analysisType);
        double[] timeLimitTimeStep = AnalysisUtils.getTimeLimitAndTimeTick(this, activity, cdfLength);
        return this.analyze(analysisType, timeLimitTimeStep[0], timeLimitTimeStep[1]);
    }

    public Map<SagaTask, Double> getChildrenScalableExpectedCompletionTimes() {
        return children.stream()
                .collect(Collectors.toMap(Function.identity(), SagaTask::calcScalableExpectedCompletionTime));
    }

    public double calcScalableExpectedCompletionTime() {
        Activity activity = this.composeForwardActivity();
        double[] timeLimitTimeTick = AnalysisUtils.getTimeLimitAndTimeTick(this, activity,
                Constants.ARRAY_LENGTH_FOR_PROXY_ANALYSIS);
        return calcExpectedValueFromCDF(analyze(activity, timeLimitTimeTick[0], timeLimitTimeTick[1]),
                timeLimitTimeTick[1]);
    }

    @Deprecated
    public double[] analyze() {
        Activity completeActivity = this.composeForwardActivity();
        return analyze(completeActivity, completeActivity.getFairTimeLimit(),
                completeActivity.getLeastExpectedTimeTick());
    }

    public double[] analyze(double timeLimit, double timeTick) {
        return analyze(composeForwardActivity(), timeLimit, timeTick);
    }

    public double[] analyze(Activity activity, double timeLimit, double timeTick) {
        AnalysisHeuristicsVisitor analyzer = new SDFHeuristicsVisitor(BigInteger.valueOf(2), BigInteger.valueOf(5),
                new TruncatedExponentialMixtureApproximation());
        this.lastTimeTick = timeTick;
        if (timeTick == 0) {
            throw new RuntimeException("TimeTick is equal to 0 -> timeLimit" + timeLimit);
        }
        return activity.analyze(BigDecimal.valueOf(timeLimit), BigDecimal.valueOf(timeTick), analyzer);
    }

    public double[] analyzeWithMaximumArrayLength(int arrayLength) {
        Activity completeActivity = this.composeForwardActivity();
        double[] timeLimitAndTimeTick = AnalysisUtils.getTimeLimitAndTimeTick(this, completeActivity, arrayLength);
        return this.analyze(completeActivity, timeLimitAndTimeTick[0], timeLimitAndTimeTick[1]);
    }

    public double getLastTimeTickUsed() {
        return this.lastTimeTick;
    }

    private double calcExpectedValueFromCDF(double[] cdf, double timeTick) {
        double mean = 0;
        for (double v : cdf) {
            mean += (1 - v) * timeTick;
        }
        return mean;
    }

    public List<SagaTask> getChildren() {
        return children;
    }

    public void setActivity(CompositeTask newActivity) {
        if (this.activity == newActivity)
            return;
        this.activity = newActivity;
        if (newActivity != null) {
            newActivity.setType(this);
        }
    }

    public CompositeTask getActivity() {
        return activity;
    }

    @Override
    public CompositeTaskType clone() {
        try {
            CompositeTaskType clone = (CompositeTaskType) super.clone();
            if (this.children != null) {
                clone.children = new ArrayList<>(this.children.size());
                for (SagaTask task : this.children) {
                    clone.children.add(task.clone());
                }
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    protected void checkAnalysis(Analysis[] analysis) {
        double referenceTimeLimit = analysis[0].getTimeLimit();
        double referenceTimeStep = analysis[0].getTimeStep();
        for (int i = 0; i < analysis.length; i++) {
            if (analysis[i].getTimeLimit() != referenceTimeLimit) {
                throw new RuntimeException(analysis[i].getTimeLimit() + " != " + referenceTimeLimit
                        + " -> time limits of children tasks aren't equal!");
            }
            if (analysis[i].getTimeStep() != referenceTimeStep) {
                throw new RuntimeException(analysis[i].getTimeStep() + " != " + referenceTimeStep
                        + " -> time steps of children tasks aren't equal!");
            }
        }
    }

    protected double[] fixCdfLength(double[] activityCDF, int targetCdfLength) {
        if (activityCDF.length < targetCdfLength) {
            double[] tempActivityCDF = new double[targetCdfLength];
            int ii;
            for (ii = 0; ii < activityCDF.length; ii++)
                tempActivityCDF[ii] = activityCDF[ii];
            int difference = targetCdfLength - activityCDF.length;
            for (int iii = ii; iii < ii + difference; iii++)
                tempActivityCDF[iii] = activityCDF[ii - 1];
            return tempActivityCDF;
        }
        return activityCDF;
    }

    public Activity getActivityFromAnalysisType(AnalysisType analysisType) {
        Activity activity;
        switch (analysisType) {
            case FORWARD:
                activity = this.composeForwardActivity();
                break;
            default:
                throw new RuntimeException(analysisType + ": type not implemented!");
        }
        return activity;
    }

    public abstract Activity composeWorkflow(FailCombination combination);

    public abstract Activity getCompensationWorkflow();

    public abstract JsonNode toJsonNode(ObjectMapper mapper);

    public abstract Activity composeWorkflowUntilFailure(FailCombination combination);

    public abstract Activity composeWorkflowFromFailure(FailCombination combination);

}
