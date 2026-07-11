package it.unifi.dinfo.stlab.modeling;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.oristool.eulero.evaluation.approximator.TruncatedExponentialMixtureApproximation;
import org.oristool.eulero.evaluation.heuristics.AnalysisHeuristicsVisitor;
import org.oristool.eulero.evaluation.heuristics.SDFHeuristicsVisitor;
import org.oristool.eulero.modeling.Activity;
import org.oristool.eulero.modeling.Simple;
import org.oristool.eulero.modeling.stochastictime.StochasticTime;
import org.oristool.models.gspn.GSPNTransient;
import org.oristool.models.stpn.RewardRate;
import org.oristool.models.stpn.TransientSolution;
import org.oristool.models.stpn.trans.RegTransient;
import org.oristool.models.stpn.trees.DeterministicEnablingState;
import org.oristool.petrinet.Marking;
import org.oristool.petrinet.PetriNet;
import org.oristool.util.Pair;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import it.unifi.dinfo.stlab.modeling.analysis.SagaScenarioAnalyzer;
import it.unifi.dinfo.stlab.modeling.compositeTaskType.AndTaskType;
import it.unifi.dinfo.stlab.modeling.compositeTaskType.SeqTaskType;
import it.unifi.dinfo.stlab.modeling.compositeTaskType.TaskEnumType;
import it.unifi.dinfo.stlab.modeling.compositeTaskType.XorTaskType;
import it.unifi.dinfo.stlab.modeling.utils.FailCombination;
import it.unifi.dinfo.stlab.modeling.utils.ScenarioInfo;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableDeterministicTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableStochasticTime;

/**
 * Abstract root of the SAGA task hierarchy, modeled as a Composite pattern.
 *
 * <p>
 * Each {@code SagaTask} represents a unit of work within a SAGA workflow and
 * can be either a leaf node ({@link SimpleTask}, mapping to a single
 * microservice invocation) or a composite node ({@link CompositeTask}, grouping
 * children via SEQ, AND, or XOR composition operators).
 *
 * <h3>Dual Activity Decomposition</h3>
 * <p>
 * Every task exposes two orthogonal time components for the Eulero stochastic
 * analysis engine:
 * <ul>
 *   <li><b>Scalable activity</b> — the portion of processing time that
 *       decreases with horizontal scaling (e.g., CPU-bound computation).</li>
 *   <li><b>Unscalable activity</b> — the fixed overhead that remains constant
 *       regardless of scaling (e.g., network latency, serialization).</li>
 * </ul>
 * The <em>complete</em> activity is the sequential composition of both.
 *
 * <h3>Failure and Compensation Model</h3>
 * <p>
 * Each task may carry a failure probability ({@link #probToFail}), a failure
 * time distribution ({@link #failureTimeDistribution}), and a compensation
 * time ({@link #compensationTime}). These parameters drive the generation of
 * failure scenarios by {@link ScenarioBuilder}: when a task fails, all
 * previously committed tasks are compensated in topology-aware order (reverse
 * for SEQ, parallel for AND, branch-selected for XOR).
 *
 * <h3>CDF Analysis</h3>
 * <p>
 * The {@code analyzeAndSave} family of methods delegates to the Eulero engine's
 * {@link org.oristool.eulero.evaluation.heuristics.SDFHeuristicsVisitor} to
 * compute the Cumulative Distribution Function (CDF) of the task's completion
 * time. Results are cached in {@link #lastAnalysis} keyed by
 * {@link AnalysisType}.
 *
 * @see SimpleTask
 * @see CompositeTask
 * @see ScenarioBuilder
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "enumType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleTask.class, name = "SIMPLE"),
})
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE)
public abstract class SagaTask implements Cloneable {

    /** Unique identifier for this task within the workflow tree. */
    protected String name;

    /** Discriminator for Jackson polymorphic deserialization ({@code SIMPLE}, {@code SEQ}, {@code AND}, {@code XOR}). */
    protected TaskEnumType enumType;

    /** Stochastic time distribution for the compensation action of this task. */
    protected StochasticTime compensationTime;

    /**
     * Stochastic time distribution modeling the time elapsed from the start of
     * this task until its failure is detected. Used only when
     * {@link #probToFail} &gt; 0.
     */
    protected StochasticTime failureTimeDistribution;

    /** Cache of the most recent CDF analysis results, keyed by {@link AnalysisType}. */
    protected Map<AnalysisType, Analysis> lastAnalysis;

    /**
     * Creates a new task with the given name, type discriminator, and resource cost.
     *
     * @param name      unique identifier within the workflow
     * @param enumType  type discriminator for serialization
     */
    public SagaTask(String name, TaskEnumType enumType) {
        this.name = name;
        this.enumType = enumType;
        this.lastAnalysis = new HashMap<>();
    }

    /**
     * Returns a deep copy of this task, including a deep copy of
     * the {@link #lastAnalysis} cache.
     */
    @Override
    public SagaTask clone() {
        try {
            SagaTask clone = (SagaTask) super.clone();
            clone.setLastAnalysis(this.copyLastAnalysis());
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Creates a deep copy of the {@link #lastAnalysis} map, cloning
     * each {@link Analysis} value independently.
     *
     * @return a new map with cloned analysis entries
     */
    public Map<AnalysisType, Analysis> copyLastAnalysis() {
        if (this.lastAnalysis == null) {
            return new HashMap<>();
        }
        Map<AnalysisType, Analysis> deepCopy = new HashMap<>();
        for (Map.Entry<AnalysisType, Analysis> entry : this.lastAnalysis.entrySet()) {
            AnalysisType key = entry.getKey();
            Analysis originalValue = entry.getValue();
            Analysis clonedValue = (originalValue != null) ? originalValue.clone() : null;
            deepCopy.put(key, clonedValue);
        }
        return deepCopy;
    }

    /**
     * Returns the Eulero {@link Activity} representing only the scalable
     * (horizontally improvable) portion of this task's processing time.
     *
     * @return the forward activity obtained by composition
     */
    public abstract Activity getForwardActivity();

    /**
     * Returns an Eulero {@link Activity} that models this task's execution
     * including probabilistic branching between success and failure.
     * The returned activity overrides {@code buildSTPN} to generate
     * a Stochastic Time Petri Net with enabling functions that enforce
     * fail-stop semantics across parallel branches.
     *
     * @param workflowServiceNames names of all services in the AND-block,
     *                              used to generate STPN enabling functions
     * @return the failure-aware activity
     * @see SimpleTask#buildFailureSTPN
     */
    public abstract Activity getActivityWithFailure(List<String> workflowServiceNames);

    public PetriNet getStpnModel() {
        PetriNet stpn = new PetriNet();
        
        stpn.addPlace("START");
        stpn.addPlace("END");

        Activity activity = this.getActivityWithFailure(this.getSimpleTasks().stream().map(SagaTask::getName).toList());
        activity.buildSTPN(stpn, stpn.getPlace("START"), stpn.getPlace("END"), 0);

        return stpn;
    }

    public Activity getStpnModelForProbabilityCalculus() {
        List<String> allServices = this.getSimpleTasks().stream().map(SimpleTask::getName).toList();
        return this.getActivityWithFailure(allServices);
    }

    /**
     * New version: returns the happy path (all-success) execution activity for this task
     *
     * @return the happy path activity created in a compositional way
     */
    public Activity getHappyPathActivity() {
        return this.getForwardActivity();
    }

    /**
     * V2: returns the happy path probability by composition
     * 
     * @return the happy path probability
     */
    public abstract double getHappyPathProbability();

    /**
     * @return the probability to fail the happy path
     */
    public double getProbToFail() {
        return 1.0 - this.getHappyPathProbability();
    }

    /**
     * Computes the CDF of this task's completion time using the Eulero engine.
     *
     * @param timeLimit maximum time horizon for the analysis (seconds)
     * @param timeTick  time step between CDF samples (seconds)
     * @return the CDF array where {@code cdf[i] = P(completion ≤ i * timeTick)}
     */
    public abstract double[] analyze(double timeLimit, double timeTick);

    /**
     * Computes and caches the CDF for the given analysis type using
     * engine-default time parameters.
     *
     * @param analysisType the component to analyze (SCALABLE, UNSCALABLE, or COMPLETE)
     * @return the analysis result
     * @deprecated Use {@link #analyzeAndSave(AnalysisType, int)} with explicit CDF length
     */
    @Deprecated
    public abstract Analysis analyzeAndSave(AnalysisType analysisType);

    /**
     * Computes and caches the CDF for the given analysis type, constraining
     * the output array to the specified maximum length.
     *
     * @param analysisType   the component to analyze
     * @param lengthCdfArray maximum number of CDF samples
     * @return the analysis result
     */
    public abstract Analysis analyzeAndSave(AnalysisType analysisType, int lengthCdfArray);

    /**
     * Computes and caches the CDF for the given analysis type with explicit
     * time parameters.
     *
     * @param analysisType the component to analyze
     * @param timeLimit    maximum time horizon (seconds)
     * @param timeStep     time step between CDF samples (seconds)
     * @return the analysis result
     */
    public abstract Analysis analyzeAndSave(AnalysisType analysisType, double timeLimit, double timeStep);
    /**
     * Calculates the concurrency groups of the subtree.
     * A concurrency group indicates a set of simple tasks where one of them fail and at the same time 
     * the others are in execution.
     * @return a set containing all the concurrency groups
     */
    public abstract List<List<SimpleTask>> getConcurrencyGroups();

    /**
     * Calculates all the scenarios of this workflow except for the happy path.
     * The happy path is the workflow itself in the "forward" direction.
     * @return A list of all the scenarios
     */
    public List<FailCombination> getFailableCombinations() {
        List<List<SimpleTask>> concurrencyGroups = this.getConcurrencyGroups();
        
        List<FailCombination> failCombinations = new ArrayList<>();

        for (List<SimpleTask> group : concurrencyGroups) {
            failCombinations.addAll(FailCombination.fromServiceGroup(group, this.getStpnModel()));
        }

        failCombinations.add(FailCombination.happyPath());

        return failCombinations;
    }

    /**
     * Calculate the string used as reward in the analysis of a STPN. 
     * The reward represents all the possible combinations of failing scenarios.
     * @return The string representing the reward
     */
    public String getAllRewardCombinations() {
        List<FailCombination> failCombinations = this.getFailableCombinations();

        return this.getAllRewardCombinations(failCombinations);
    }

    private String getAllRewardCombinations(List<FailCombination> failCombinations) {
        String reward = "";

        for (int i = 0; i < failCombinations.size(); i++) {
            if (i != failCombinations.size()-1)
                reward += failCombinations.get(i).getRewardExpression() + ";";
            else
                reward += failCombinations.get(i).getRewardExpression();
        }

        return reward;
    }

    /**
     * Convenience method that invokes {@link #analyzeAndSave(AnalysisType, int)}
     * for every {@link AnalysisType} (SCALABLE, UNSCALABLE, COMPLETE).
     *
     * @param lengthCdfArray maximum number of CDF samples
     */
    public void analyzeAndSaveAll(int lengthCdfArray) {
        for (AnalysisType type : AnalysisType.values()) {
            this.analyzeAndSave(type, lengthCdfArray);
        }
    }

    /**
     * Computes the CDF of the complete activity, automatically adjusting the
     * time step so that the resulting array length does not exceed the given
     * maximum.
     *
     * @param arrayLength maximum CDF array length
     * @return the CDF array
     */
    public abstract double[] analyzeWithMaximumArrayLength(int arrayLength);

    /**
     * Computes the expected value of the scalable component of the
     * completion time.
     *
     * @return E[scalable time] in seconds
     */
    public abstract double calcScalableExpectedCompletionTime();

    /**
     * Returns a flat list of all {@link SimpleTask} leaf nodes in this subtree,
     * collected in depth-first order.
     *
     * @return list of leaf tasks
     */
    public abstract List<SimpleTask> getSimpleTasks();

    /**
     * Factory method that creates a {@link SimpleTask} leaf node from
     * variable stochastic time distributions.
     *
     * @param name                    unique task name (maps to a microservice)
     * @param scalableDistribution    stochastic time for the scalable component
     * @return a new {@link SimpleTask}
     */
    public static SimpleTask simple(String name, VariableStochasticTime scalableDistribution, double probToFail) {
        return new SimpleTask(name,
                new Simple(name + "_scalable", (StochasticTime) scalableDistribution), probToFail);
    }

    /**
     * Factory method that creates a {@link SimpleTask} leaf node from separate
     * stochastic time distributions for the the forward execution and compensation activities.
     * @param name                      unique task name (maps to a microservice)
     * @param forwardDistribution       stochastic time for the forward execution (scalable component)
     * @param compensationDistribution  stochastic time for the compensation activity
     * @param failureDistribution       stochastic time until failure detection
     * @param probToFail                probability that this task fails during execution
     * @return a new {@link SimpleTask} with the specified parameters
     */
    public static SimpleTask simple(
        String name, 
        VariableStochasticTime forwardDistribution, 
        VariableStochasticTime compensationDistribution, 
        VariableStochasticTime failureDistribution, 
        double probToFail
    ) {
        return new SimpleTask(name, (StochasticTime)forwardDistribution, probToFail, (StochasticTime)compensationDistribution, (StochasticTime)failureDistribution);
    }

    /**
     * Factory method that creates a SEQ (sequential) composite task.
     * Children are executed in order.
     *
     * @param name                unique task name
     * @param hybridScalableTasks children to be composed sequentially
     * @return a new {@link CompositeTask} with {@link SeqTaskType}
     */
    public static CompositeTask seq(String name, SagaTask... hybridScalableTasks) {
        SeqTaskType seqType = new SeqTaskType(Arrays.asList(hybridScalableTasks));
        return new CompositeTask(name, seqType);
    }

    /**
     * Factory method that creates a XOR (exclusive choice) composite task.
     * Exactly one child branch is executed, selected with the given
     * probabilities.
     *
     * @param name                unique task name
     * @param hybridScalableTasks child branches
     * @param probabilities       selection probability for each branch (must sum to 1.0)
     * @return a new {@link CompositeTask} with {@link XorTaskType}
     */
    public static CompositeTask xor(String name, List<SagaTask> hybridScalableTasks,
            List<Double> probabilities) {
        XorTaskType xorType = new XorTaskType(hybridScalableTasks, probabilities);
        return new CompositeTask(name, xorType);
    }

    public static CompositeTask xor(String name, Map<SagaTask, Double> branchMap ) {
        XorTaskType xorType = new XorTaskType(branchMap);
        return new CompositeTask(name, xorType);
    }

    /**
     * Factory method that creates an AND (parallel fork-join) composite task.
     * All children are executed concurrently; the task completes when the
     * slowest child finishes. Nested AND blocks are automatically flattened
     * since AND composition is associative.
     *
     * @param name                unique task name
     * @param hybridScalableTasks children to be composed in parallel
     * @return a new {@link CompositeTask} with {@link AndTaskType}
     */
    public static CompositeTask and(String name, SagaTask... hybridScalableTasks) {
        // Flatten nested AND blocks: AND(AND(a,b), c) → AND(a, b, c)
        // This is safe because AND is associative (all children run in parallel).
        List<SagaTask> flattened = new java.util.ArrayList<>();
        for (SagaTask child : hybridScalableTasks) {
            if (child instanceof CompositeTask composite
                    && composite.getType().getEnumType() == TaskEnumType.AND) {
                flattened.addAll(composite.getChildren());
            } else {
                flattened.add(child);
            }
        }
        AndTaskType andType = new AndTaskType(flattened);
        return new CompositeTask(name, andType);
    }

    /** Returns the unique name of this task. */
    public String getName() {
        return this.name;
    }

    /** Returns the type discriminator (SIMPLE, SEQ, AND, XOR). */
    public TaskEnumType getEnumType() {
        return this.enumType;
    }

    /** Returns the stochastic time distribution for compensation, or {@code null}. */
    public StochasticTime getCompensationTime() {
        return compensationTime;
    }

    /** Sets the stochastic time distribution for compensation. */
    public void setCompensationTime(StochasticTime compensationTime) {
        this.compensationTime = compensationTime;
    }

    /** Returns the failure time distribution, or {@code null} if not set. */
    public StochasticTime getFailureTimeDistribution() {
        return failureTimeDistribution;
    }

    /** Sets the failure time distribution. */
    public void setFailureTimeDistribution(StochasticTime failureTimeDistribution) {
        this.failureTimeDistribution = failureTimeDistribution;
    }

    /** Sets the unique name of this task. */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the cached analysis result for the given type,
     * or {@code null} if not yet computed.
     *
     * @param analysisType the analysis type to retrieve
     * @return the cached analysis, or {@code null}
     */
    public Analysis getLastAnalysis(AnalysisType analysisType) {
        return this.lastAnalysis.get(analysisType);
    }

    /** Replaces the entire analysis cache. */
    public void setLastAnalysis(Map<AnalysisType, Analysis> lastAnalysis) {
        this.lastAnalysis = lastAnalysis;
    }

    /**
     * Tommaso implementation for a method that computes the time to consistency using methods
     * of Edoardo implementation. Returns an Analysis object to guarantee the same interface and object classes as icpe26 repository.
     * This method returns the total cdf of all scenarios, weighted by their probabilities, representing the overall time to consistency distribution for the entire SAGA workflow.
     * 
     * @return an Analysis object containing the global CDF of time to consistency, or null if no scenarios were analyzed
     */
    public Analysis analyzeTimeToConsistency() {
        List<ScenarioInfo> allScenarios = this.calculateAllScenarioAnalysis();
        return ScenarioInfo.getTimeToConsistencyScenario(allScenarios).getAnalysis();
    }

    public Analysis analyzeTimeToFailure() {
        // 1. Calcolo le probabilità di fallimento dei simple tasks
        Map<FailCombination, Double> probabilityMap = this.calculateProbabilityMapForAllFailCombinations();

        // 2. Recupero le fail combinations e rimuovo quella di happy path che non ci interessa
        List<FailCombination> failCombinations = this.getFailableCombinations();
        failCombinations.removeIf(
            failCombination -> {
                return failCombination.isHappyPath();
            }
        );
        
        // 3. Calcolo i workflows associati ad ogni fail combination
        Map<FailCombination, Activity> workflowMap = new HashMap<>();
        for (FailCombination failCombination : failCombinations)
            workflowMap.put(failCombination, this.composeWorkflowUntilFailure(failCombination));

        // 4. Calcolo l'analisi associata ad ogni fail combinations
        Map<FailCombination, Analysis> analysisMap = this.calculateAnalysisMap(workflowMap);

        // 5. Costruisco gli scenari
        List<ScenarioInfo> allScenarios = new ArrayList<>();
        for (FailCombination failCombination : failCombinations) {
            allScenarios.add(
                ScenarioInfo.failingScenario(
                    analysisMap.get(failCombination), 
                    probabilityMap.get(failCombination), 
                    failCombination
                )
            );
        }

        // 6. Interpolo gli scenari e ritorno la mixture
        SagaScenarioAnalyzer.interpolateAllScenarios(allScenarios);
        return SagaScenarioAnalyzer.mixture(allScenarios, true);
    }

    public Analysis analizeTimeFromFailure() {
        // 1. Calcolo le probabilità di fallimento dei simple tasks
        Map<FailCombination, Double> probabilityMap = this.calculateProbabilityMapForAllFailCombinations();

        // 2. Recupero le fail combinations e rimuovo quella di happy path che non ci interessa
        List<FailCombination> failCombinations = this.getFailableCombinations();
        failCombinations.removeIf(
            failCombination -> {
                return failCombination.isHappyPath();
            }
        );
        
        // 3. Calcolo i workflows associati ad ogni fail combination
        Map<FailCombination, Activity> workflowMap = new HashMap<>();
        for (FailCombination failCombination : failCombinations)
            workflowMap.put(failCombination, this.composeWorkflowFromFailure(failCombination));

        // 4. Calcolo l'analisi associata ad ogni fail combinations
        Map<FailCombination, Analysis> analysisMap = this.calculateAnalysisMap(workflowMap);

        // 5. Costruisco gli scenari
        List<ScenarioInfo> allScenarios = new ArrayList<>();
        for (FailCombination failCombination : failCombinations) {
            allScenarios.add(
                ScenarioInfo.failingScenario(
                    analysisMap.get(failCombination), 
                    probabilityMap.get(failCombination), 
                    failCombination
                )
            );
        }

        // 6. Interpolo gli scenari e ritorno la mixture
        SagaScenarioAnalyzer.interpolateAllScenarios(allScenarios);
        return SagaScenarioAnalyzer.mixture(allScenarios, true);
    }

    private Map<FailCombination, Double> calculateProbabilityMapForAllFailCombinations() {
        List<FailCombination> failCombinations = this.getFailableCombinations();
        Map<FailCombination, Activity> workflowMap = this.calculateActivityForEachFailCombination(failCombinations);
        Map<FailCombination, Analysis> analysisMap = this.calculateAnalysisMap(workflowMap);

        double maxTimeLimit = this.getMaxTimeLimit(analysisMap.values());

        return this.calculateProbabilityMap(failCombinations, maxTimeLimit);
    }

    private Map<FailCombination, Activity> calculateActivityForEachFailCombination(List<FailCombination> failCombinations) {
        Map<FailCombination, Activity> workflowMap = new HashMap<>();

        for (FailCombination failCombination : failCombinations) {
            workflowMap.put(failCombination, this.composeWorkflow(failCombination));
        }

        return workflowMap;
    }

    /**
     * It calculates the time to consistency of a SagaTask.
     * This is done in multiple phases:
     * <ol>
     * <li>Fail combination calculation</li>
     * <li>Workflow creation for each fail combination</li>
     * <li>Initial workflow analysis: in this phase the analysis will be used to get a correct time limit. Then the time limit will be
     * used as time limit in the regenerative analysis</li>
     * <li>Probabilities calculation: with regenerative transient analysis we can calculate the probability of each fail combination</li>
     * <li>New time limit and time step calculation: based on the probability we calculate a new time limit based on the max time limit
     * between the analysis that have a probability greater than zero</li>
     * <li>Mixture calculation: the mixture of the CDFs is calculated</li>
     * </ol>
     * @param root
     * @return
     */
    public List<ScenarioInfo> calculateAllScenarioAnalysis() {
        // 1. Calculates all the fail combinations
        List<FailCombination> failCombinations = this.getFailableCombinations();
        System.out.println("Numero combinazioni trovate: " + failCombinations.size());
        
        Map<FailCombination, Activity> workflowMap = new HashMap<>();

        // 2. For each fail combination we calculate the respective workflow
        for (FailCombination failCombination : failCombinations) {
            workflowMap.put(failCombination, this.composeWorkflow(failCombination));
        }

        // 3. Analysis of all the workflows to calculate a correct time limit.
        //    This time limit will be used to execute the transient analysis.
        //    The time step will be calculate by division of the time limit by the constant number of time step needed.
        Map<FailCombination, Analysis> analysisMap = this.calculateAnalysisMap(workflowMap);

        double maxTimeLimit = this.getMaxTimeLimit(analysisMap.values());
        
        // 4. Probabilities calculation: for each fail combination it returns the probability of that scenario
        Map<FailCombination, Double> probabilityMap = this.calculateProbabilityMap(failCombinations, maxTimeLimit);

        // 5a. I delete all the analysis associate to a probability equals to zero.
        //    So I can modify the time limit with the maximum time limit of the original analysis
        Map<FailCombination, Activity> workflowsToBeAnalyzed = new HashMap<>();

        double updatedMaxTimeLimit = Double.MIN_VALUE;
        double updatedMinTimeStep = Double.MAX_VALUE;
        for (FailCombination failCombination : failCombinations) {
            if (probabilityMap.get(failCombination) > 0.0) {
                if (analysisMap.get(failCombination).getTimeLimit() > updatedMaxTimeLimit)
                    updatedMaxTimeLimit = analysisMap.get(failCombination).getTimeLimit();
                if (analysisMap.get(failCombination).getTimeStep() < updatedMinTimeStep)
                    updatedMinTimeStep = analysisMap.get(failCombination).getTimeStep();
                workflowsToBeAnalyzed.put(failCombination, workflowMap.get(failCombination));
            }
        }

        // 5b. Instead of do again the analysis I create the interpolation based on
        //     the min time step and the max time limit
        Map<FailCombination, Analysis> updatedAnalysis = new HashMap<>();

        System.out.println("Interpolo con step=" + updatedMinTimeStep + " e limit=" + updatedMaxTimeLimit);
        
        for (FailCombination failCombination : workflowsToBeAnalyzed.keySet()) {
            Analysis analysis = analysisMap.get(failCombination).resample(updatedMinTimeStep, updatedMaxTimeLimit);
            updatedAnalysis.put(failCombination, analysis);
        }

        // 6a. We construct the map of analysis associated to the probability for the mixture calculation
        Map<Analysis, Double> analysisToBeMixed = new HashMap<>();
        // At the end of the loop in allScenarios we have the happy path and the failing scenarios.
        // At that point the scenarios aren't grouped by service
        List<ScenarioInfo> allScenarios = new ArrayList<>();

        for (FailCombination failCombination : updatedAnalysis.keySet()) {
            analysisToBeMixed.put(updatedAnalysis.get(failCombination), probabilityMap.get(failCombination));
            allScenarios.add(ScenarioInfo.failingScenario(updatedAnalysis.get(failCombination), probabilityMap.get(failCombination), failCombination));
            System.out.println(failCombination + "\nProbability: " + probabilityMap.get(failCombination));
        }
        
        List<ScenarioInfo> resultsScenarios = new ArrayList<>();
        // 6b. Finally we can calculate the mixture of the analysis
        Analysis totalAnalysis = SagaScenarioAnalyzer.mixture(analysisToBeMixed);
        resultsScenarios.add(ScenarioInfo.timeToConsistency(totalAnalysis));

        SagaScenarioAnalyzer analyzer = new SagaScenarioAnalyzer();
        resultsScenarios.addAll(analyzer.getGroupedScenarios(allScenarios, true));

        // 7. Generate the return list
        return resultsScenarios;
    }

    private Map<FailCombination, Analysis> calculateAnalysisMap(Map<FailCombination, Activity> workflowMap) {
        Map<FailCombination, Analysis> analysisMap = new HashMap<>();

        for (FailCombination failCombination : workflowMap.keySet()) {
            System.out.println("Calcolo analisi per workflow associato a FailCombination " + failCombination.getRewardExpression() + " ...");
            double[] timeLimitTimeStep = AnalysisUtils.getTimeLimitAndTimeTick(workflowMap.get(failCombination), Constants.RECOMMENDED_ANALYSIS_ARRAY_LENGTH);
            System.out.println("Time limit: " + timeLimitTimeStep[0]);
            System.out.println("Time step: " + timeLimitTimeStep[1]);
            AnalysisHeuristicsVisitor analyzer = new SDFHeuristicsVisitor(BigInteger.valueOf(2), BigInteger.valueOf(5),
                new TruncatedExponentialMixtureApproximation());

            double[] cdf = workflowMap.get(failCombination).analyze(BigDecimal.valueOf(timeLimitTimeStep[0]), BigDecimal.valueOf(timeLimitTimeStep[1]), analyzer);

            Analysis analysis = new Analysis(cdf, timeLimitTimeStep[0], timeLimitTimeStep[1]);

            analysisMap.put(failCombination, analysis);
        }

        return analysisMap;
    }

    private double getMaxTimeLimit(Collection<Analysis> analysis) {
        double maxTimeLimit = Double.MIN_VALUE;

        for (Analysis a : analysis) {
            if (a.getTimeLimit() > maxTimeLimit)
                maxTimeLimit = a.getTimeLimit();
        }

        return maxTimeLimit;
    }

    /**
     * This function creates the total reward from the list of fail combinations.
     * This reward is a string and it will be used in the transient analysis.
     * Then the transient analysis of the petri net of the workflow is done by put the max time limit and the corresponding time step.
     * The solution of the transient analysis is the probability of each scenario.
     * 
     * @param failCombinations
     * @param maxTimeLimit
     * @return
     */
    private Map<FailCombination, Double> calculateProbabilityMap(List<FailCombination> failCombinations, double maxTimeLimit) {
        Map<FailCombination, Double> probabilityMap = new HashMap<>();
        PetriNet petriNet = this.getStpnModel();

        BigDecimal timeStep = BigDecimal.valueOf(maxTimeLimit).divide(BigDecimal.valueOf(Constants.REGENERATIVE_TRANSIENT_ANALYSIS_N_TIME_STEP), 8, RoundingMode.HALF_UP);

        System.out.println("Costruisco analisi transiente con TimeLimt = " + maxTimeLimit + " e TimeStep = " + timeStep);

        String reward = this.getAllRewardCombinations(failCombinations);

        Marking marking = new Marking();
        marking.setTokens(petriNet.getPlace("START"), 1);

        RegTransient.Builder builder = RegTransient.builder();
        builder.timeBound(BigDecimal.valueOf(maxTimeLimit));
        builder.timeStep(timeStep);
        builder.markingFilter(RewardRate.nonZero(0.0, TransientSolution.rewardRates(reward)));
        RegTransient analysis = builder.build();

        System.out.println("Start transient analysis");
        TransientSolution<DeterministicEnablingState, Marking> result = analysis.compute(petriNet, marking);
        System.out.println("compute() executed");
        TransientSolution<DeterministicEnablingState, RewardRate> rewardResult = TransientSolution.computeRewards(false, result, TransientSolution.rewardRates(reward));
        System.out.println("computeRewards() executed");

        double[][][] solution = rewardResult.getSolution();

        this.checkProbabilitySum(solution);

        //rewardResult.writeCSV("solution.csv", 4);

        int i = 0;
        for (FailCombination failCombination : failCombinations) {
            probabilityMap.put(failCombination, solution[solution.length-1][0][i]);
            i++;
        }

        return probabilityMap;
    }

    public double[] getTransientFromReward(PetriNet petriNet, Marking marking, String allRewards, double timeLimit){
        Pair<Map<Marking, Integer>, double[][]> transientResult = GSPNTransient.builder()
                                                        .timePoints(new double[]{timeLimit})
                                                        .build() 
                                                        .compute(petriNet, marking); 
        
        String[] dividedRewards = allRewards.split(";");

        Map<Marking, Integer> statePos = transientResult.first();
        double[][] probs = transientResult.second();

        double[] rewardValues = new double[dividedRewards.length];

        for (Map.Entry<Marking, Integer> entry : statePos.entrySet()) {
            Marking m = entry.getKey();
            double prob = probs[0][entry.getValue()];

            for (int i = 0; i < dividedRewards.length; i++) {
                rewardValues[i] += prob * RewardRate.fromString(dividedRewards[i]).evaluate(timeLimit, m);
            }
        }
        
        return rewardValues;
    }

    private void checkProbabilitySumTransient(double[] solution) {
        double sum = 0.0;
        for (int i = 0; i < solution.length; i++) {
            double value = solution[i];
            sum += value;
        }

        if (Math.abs(sum - 1.0) > Constants.ERROR_REGENERATIVE_ANALYSIS_ALLOWED)
            throw new RuntimeException("The transient analysis doesn't sum to 1 -> " + sum + " != 1-" + Constants.ERROR_REGENERATIVE_ANALYSIS_ALLOWED);
    }

    private void checkProbabilitySum(double[][][] solution) {
        double sum = 0.0;
        for (int i = 0; i < solution[0][0].length; i++) {
            double value = solution[solution.length-1][0][i];
            sum += value;
        }
        if (Math.abs(sum - 1.0) > Constants.ERROR_REGENERATIVE_ANALYSIS_ALLOWED)
            throw new RuntimeException("The regenerative analysis doesn't sum to 1 -> " + sum + " != 1-" + Constants.ERROR_REGENERATIVE_ANALYSIS_ALLOWED);
    }

    /**
     * It builds the workflow based on the fail combination.
     * @param combination fail combination to construct the associated workflow
     * @return the composed workflow
     */
    public abstract Activity composeWorkflow(FailCombination combination);

    /**
     * It builds the forward workflow until failure. It adds to this workflow the failure distribution
     * and it discards the forward distribution of the services in execution.
     * @param combination
     * @return
     */
    public abstract Activity composeWorkflowUntilFailure(FailCombination combination);


    /**
     * It builds the backward workflow form the Fail Combination.
     * In particular the forward phase is zero and the backward phase is given by
     * @param combination
     * @return
     */
    public abstract Activity composeWorkflowFromFailure(FailCombination combination);

    /**
     * It builds the backward workflow (the reversed direction).
     * @return the backward workflow
     */
    public abstract Activity getCompensationWorkflow();

    /**
     * Check if the fail combination is contained in this task.
     * <p>
     * TODO controllare anche qui se equals funziona o se è meglio affidarsi ad un id
     * 
     * @param combination
     * @return
     */
    public abstract boolean containsFailCombination(FailCombination combination);

    /**
     * Maps an {@link AnalysisType} to the corresponding Eulero {@link Activity}
     * for this task (FORWARD → forward).
     *
     * @param analysisType the type to resolve
     * @return the corresponding activity
     * @throws RuntimeException if the analysis type is not recognized
     */
    protected Activity getActivityFromAnalysisType(AnalysisType analysisType) {
        Activity activity;
        switch (analysisType) {
            case FORWARD:
                activity = this.getForwardActivity();
                break;
            default:
                throw new RuntimeException(analysisType + ": type not implemented!");
        }
        return activity;
    }

    public String workflowString() {
        return this.workflowStringRecursive(new StringBuffer(), 0);
    }

    public abstract String workflowStringRecursive(StringBuffer sb, int level);

    public abstract JsonNode toJsonNode(ObjectMapper mapper);

    public void exportTopologyJson(String jsonPath){
        ObjectMapper mapper = new ObjectMapper();
        try {
            File file = new File(jsonPath);

            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file, this.toJsonNode(mapper));
            System.out.println("Topology exported as JSON at: " + file.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error while exporting JSON topology: " + e.getMessage());
                e.printStackTrace();
        }
    }

    public void exportSimpleTasksToJson(String basePath) {
        List<SimpleTask> simpleTasks = this.getSimpleTasks();
        ObjectMapper mapper = new ObjectMapper();
        try {
            // create dir if not exist
            Path directoryPath = Paths.get(basePath);
            Files.createDirectories(directoryPath);

            for (SimpleTask task : simpleTasks) {

                JsonNode taskJson = task.getSimpleTasktoJson(mapper);
                String fileName = task.getName().replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
                String completeFileName = fileName + ".json";

                File destinationFile = directoryPath.resolve(completeFileName).toFile();

                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(destinationFile, taskJson);

                System.out.println("saved: " + destinationFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Error while exporting tasks to JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
