package it.unifi.dinfo.stlab.modeling;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.oristool.eulero.evaluation.approximator.TruncatedExponentialMixtureApproximation;
import org.oristool.eulero.evaluation.heuristics.AnalysisHeuristicsVisitor;
import org.oristool.eulero.evaluation.heuristics.SDFHeuristicsVisitor;
import org.oristool.eulero.modeling.Activity;
import org.oristool.eulero.modeling.ModelFactory;
import org.oristool.eulero.modeling.Simple;
import org.oristool.eulero.modeling.stochastictime.*;
import org.oristool.math.function.GEN;
import org.oristool.models.pn.PostUpdater;
import org.oristool.models.pn.Priority;
import org.oristool.models.stpn.MarkingExpr;
import org.oristool.models.stpn.SteadyStateSolution;
import org.oristool.models.stpn.TransientSolution;
import org.oristool.models.stpn.steady.RegSteadyState;
import org.oristool.models.stpn.trans.RegTransient;
import org.oristool.models.stpn.trans.TreeTransient;
import org.oristool.models.stpn.trees.DeterministicEnablingState;
import org.oristool.models.stpn.trees.StochasticTransitionFeature;
import org.oristool.petrinet.EnablingFunction;
import org.oristool.petrinet.Marking;
import org.oristool.petrinet.PetriNet;
import org.oristool.petrinet.Place;
import org.oristool.petrinet.Postcondition;
import org.oristool.petrinet.Transition;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.stpn.STPNSimulatorComponentsFactory;
import org.oristool.util.xpnCreation.XpnGenerator;
import org.oristool.util.xpnCreation.xpnAbstraction.XpnTransition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.unifi.dinfo.stlab.modeling.compositeTaskType.TaskEnumType;
import it.unifi.dinfo.stlab.modeling.utils.FailCombination;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableStochasticTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableTruncatedExponentialTime;

/**
 * Leaf node in the SAGA task hierarchy, representing a single microservice
 * invocation.
 *
 * <p>
 * Each {@code SimpleTask} wraps two Eulero {@link Simple} activities — one for
 * the scalable processing component (e.g., CPU-bound work that decreases with
 * horizontal scaling) and one for the unscalable overhead (e.g., network
 * latency, serialization). Together they form the <em>complete</em> activity
 * whose CDF is analysed by the Eulero engine.
 *
 * <h3>STPN Failure Modeling</h3>
 * <p>
 * When used inside an AND-block, {@link #getActivityWithFailure(List)} returns
 * a {@link Simple} override whose {@code buildSTPN} method generates a
 * Stochastic Time Petri Net encoding:
 * <ul>
 *   <li>A probabilistic immediate transition splitting the token into a
 *       <em>happy</em> path (weight {@code 1 − probToFail}) and a <em>failure</em>
 *       path (weight {@code probToFail}).</li>
 *   <li>Each path fires a timed transition with the same processing time
 *       distribution.</li>
 *   <li>On success, a token is deposited into the {@code end<Service>} place,
 *       enabling downstream transitions.</li>
 *   <li>On failure, a token is deposited into the {@code end<Service>Fail}
 *       place, which blocks all remaining sibling tasks via STPN enabling
 *       functions (fail-stop semantics).</li>
 * </ul>
 *
 * <h3>Compensation</h3>
 * <p>
 * If a {@link #compensationTime} is defined, {@link #getCompensationActivity()}
 * returns a {@link Simple} activity modeling the compensation time distribution.
 * This is used by {@link it.unifi.dinfo.stlab.modeling.scenario.ScenarioBuilder}
 * to compose the compensation phase of failure scenarios.
 *
 * @see SagaTask
 * @see CompositeTask
 * @see it.unifi.dinfo.stlab.modeling.stpn.ANDBlockSTPNBuilder
 */
public class SimpleTask extends SagaTask {

    /** Global counter for generating unique STPN place/transition names across instances. */
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);

    /** Eulero activity for the scalable (horizontally improvable) processing component. */
    private Simple scalableActivity;

    private double probToFail;

    /**
     * Simple constructor where compensation and failure parameters are equals to the processing distribution.
     *
     * @param name                    unique task name (maps to a microservice)
     * @param simpleScalableActivity  Eulero activity for the scalable component
     */
    @JsonCreator
    public SimpleTask(@JsonProperty("name") String name,
            @JsonProperty("scalableActivity") Simple simpleScalableActivity,
            @JsonProperty("probToFail") double probToFail) {
        super(name, TaskEnumType.SIMPLE);
        this.scalableActivity = simpleScalableActivity;
        
        this.probToFail = probToFail;
        this.compensationTime = simpleScalableActivity.getPdf().clone();
        this.failureTimeDistribution = simpleScalableActivity.getPdf().clone();
    }

    /**
     * Creates a fully-specified SimpleTask including failure and compensation
     * parameters.
     *
     * @param name                     unique task name
     * @param forwardActivity   Eulero activity for the scalable component
     * @param probToFail               probability of failure (0.0–1.0)
     * @param compensationTime         stochastic time for the compensation action
     * @param failureTimeDistribution  stochastic time until failure detection
     */
    public SimpleTask(String name,
            Simple forwardActivity,
            double probToFail,
            StochasticTime compensationTime,
            StochasticTime failureTimeDistribution) {
        super(name, TaskEnumType.SIMPLE);
        this.probToFail = probToFail;
        this.scalableActivity = forwardActivity;
        this.compensationTime = compensationTime;
        this.failureTimeDistribution = failureTimeDistribution;
    }

    /**
     * Creates a fully-specified SimpleTask including failure and compensation
     * parameters.
     *
     * @param name                     unique task name
     * @param forwardDistribution      Eulero activity for the scalable component
     * @param probToFail               probability of failure (0.0–1.0)
     * @param compensationTime         stochastic time for the compensation action
     * @param failureTimeDistribution  stochastic time until failure detection
     */
    public SimpleTask(String name,
            StochasticTime forwardDistribution,
            double probToFail,
            StochasticTime compensationTime,
            StochasticTime failureTimeDistribution) {
        super(name, TaskEnumType.SIMPLE);
        this.probToFail = probToFail;
        this.scalableActivity = new Simple(name + "_scalable", forwardDistribution);
        this.compensationTime = compensationTime;
        this.failureTimeDistribution = failureTimeDistribution;
    }

    @Override
    public Simple getForwardActivity() {
        return scalableActivity;
    }

    /** Sets the scalable activity (used when resource optimization modifies the distribution). */
    public void setScalableActivity(Simple scalableActivity) {
        this.scalableActivity = scalableActivity;
    }

    @Override
    public double[] analyze(double timeLimit, double timeTick) {
        Activity sequence = this.getForwardActivity();
        return this.analyze(sequence, timeLimit, timeTick);
    }

    @Override
    public double[] analyzeWithMaximumArrayLength(int arrayLength) {
        Activity sequence = this.getForwardActivity();
        double timeLimit = sequence.getFairTimeLimit();
        double timeTick = sequence.getLeastExpectedTimeTick();
        if ((int) (timeLimit / timeTick) > arrayLength) {
            timeTick = timeLimit / (double) arrayLength;
        }
        return this.analyze(timeLimit, timeTick);
    }

    /**
     * Runs the Eulero SDFHeuristicsVisitor on the given activity to compute
     * its CDF array.
     *
     * @param activity  the Eulero activity to analyze
     * @param timeLimit maximum time horizon (seconds)
     * @param timeTick  time step between CDF samples (seconds)
     * @return the CDF array
     * @throws RuntimeException if {@code timeTick} is zero
     */
    private double[] analyze(Activity activity, double timeLimit, double timeTick) {
        AnalysisHeuristicsVisitor analyzer = new SDFHeuristicsVisitor(BigInteger.valueOf(2), BigInteger.valueOf(5),
                new TruncatedExponentialMixtureApproximation());
        if (timeTick == 0) {
            throw new RuntimeException("TimeTick is equal to 0 and timeLimit =" + timeLimit);
        }
        return activity.analyze(BigDecimal.valueOf(timeLimit), BigDecimal.valueOf(timeTick), analyzer);
    }

    public Activity getCompensationActivity() {
        if (compensationTime == null)
            return null;
        return new Simple(name + "_comp", compensationTime);
    }

    @Override
    public double calcScalableExpectedCompletionTime() {
        return this.scalableActivity.getPdf().getExpectedValue();
    }

    /**
     * Returns a deep copy of this SimpleTask, including cloned stochastic
     * time distributions and the initial scalable activity snapshot.
     */
    @Override
    public SimpleTask clone() {
        super.clone();
        StochasticTime scalablePdf = this.scalableActivity.getPdf();
        StochasticTime scalableClone = (scalablePdf instanceof VariableStochasticTime vst)
                ? (StochasticTime) vst.cloneStochasticTime()
                : scalablePdf.clone();
        Simple clonedScalable = new Simple(name + "_scalable", scalableClone);

        SimpleTask clonedTask = new SimpleTask(name,
                clonedScalable, this.probToFail,
                this.compensationTime != null ? this.compensationTime.clone() : null,
                this.failureTimeDistribution != null ? this.failureTimeDistribution.clone() : null);
                
        return clonedTask;
    }

    @Override
    public String toString() {
        return "[SIMPLE] " + this.name;
    }

    @Override
    public List<SimpleTask> getSimpleTasks() {
        return List.of(this);
    }

    @Override
    public boolean containsFailCombination(FailCombination combination) {
        return this.equals(combination.getFailedService()) || (combination.getServicesInExecution() != null && combination.getServicesInExecution().contains(this));
    }

    //TODO accertarsi che equals sia il metodo ideale e che non sia meglio basarsi su un id
    @Override
    public Activity composeWorkflow(FailCombination combination) {
        if (combination.isHappyPath())
            return this.getForwardActivity();
        if (this.equals(combination.getFailedService())) {
            return getFailingActivity();
        }
        if (combination.getServicesInExecution() != null && combination.getServicesInExecution().contains(this)) {
            return getExecuteAndCompensateActivity();
        }
        throw new IllegalStateException("SimpleTask " + this.getName() + "not present in selected Failure Combination");
    }


    @Override
    public Activity getCompensationWorkflow() {
        return getCompensationActivity();
    }


    private Activity getFailingActivity(){
        return new Simple(name + "_failing", failureTimeDistribution);
    }

    private Activity getExecuteAndCompensateActivity(){
        return ModelFactory.sequence(getForwardActivity(), getCompensationActivity());
    }




    @Deprecated
    @Override
    public Analysis analyzeAndSave(AnalysisType analysisType) {
        Activity activity = this.getActivityFromAnalysisType(analysisType);
        double timeLimit = activity.getFairTimeLimit();
        double timeStep = activity.getLeastExpectedTimeTick();
        return new Analysis(this.analyze(timeLimit, timeStep), timeLimit, timeStep);
    }

    @Override
    public Analysis analyzeAndSave(AnalysisType analysisType, int lengthCdfArray) {
        Activity activity = this.getActivityFromAnalysisType(analysisType);
        double[] timeLimitTimeStep = AnalysisUtils.getTimeLimitAndTimeTick(activity, lengthCdfArray);
        return this.analyzeAndSave(analysisType, timeLimitTimeStep[0], timeLimitTimeStep[1]);
    }

    @Override
    public Analysis analyzeAndSave(AnalysisType analysisType, double timeLimit, double timeStep) {
        Activity activity = this.getActivityFromAnalysisType(analysisType);
        Analysis result = new Analysis(this.analyze(activity, timeLimit, timeStep), timeLimit, timeStep);
        this.lastAnalysis.put(analysisType, result);
        return result;
    }

    /**
     * {@inheritDoc}
     * <p>Returns a {@link Simple} override whose {@code buildSTPN} method
     * delegates to {@link #buildFailureSTPN}, generating the full success/failure
     * branching structure in the Petri Net.
     */
    @Override
    public Activity getActivityWithFailure(List<String> workflowServiceNames) {
        final StochasticTime clonedPdf = this.scalableActivity.getPdf().clone();
        final StochasticTime clonedPdfFailure = this.failureTimeDistribution.clone();
        final double failProb = this.probToFail;
        final String taskName = this.name;

        return new Simple(taskName, clonedPdf) {
            @Override
            public int buildSTPN(PetriNet pn, Place in, Place out, int prio) {
                return buildFailureSTPN(pn, in, out, prio, taskName, clonedPdf, clonedPdfFailure, workflowServiceNames, failProb);
            }
        };
    }

    /**
     * Generates the STPN fragment encoding a single microservice invocation
     * with probabilistic success/failure branching.
     *
     * <p>The generated Petri Net structure is:
     * <pre>
     *   [in] ──probHappy──► [startHappy] ──tHappy──► [endHappy] ──toOut──► [out] + [endService]
     *     └──probFail──► [startFail] ──tFail──► [endServiceFail]
     * </pre>
     *
     * <p>Enabling functions on {@code tHappy} and {@code tFail} enforce
     * fail-stop semantics: a task can only fire if no sibling service has
     * failed yet ({@code endXFail==0} for all other services in the
     * AND-block).
     *
     * @param pn                    the Petri Net to extend
     * @param in                    input place (token triggers execution)
     * @param out                   output place (token signifies completion)
     * @param prio                  base priority for immediate transitions
     * @param serviceName           the service name for place/transition naming
     * @param pdf                   the processing time distribution
     * @param workflowServiceNames  all service names in the AND-block
     * @param probToFail            failure probability (0.0–1.0)
     * @return the next available priority level after the generated transitions
     */
    static int buildFailureSTPN(PetriNet pn, Place in, Place out, int prio,
            String serviceName, StochasticTime pdf, StochasticTime pdfFailure,
            List<String> workflowServiceNames, double probToFail) {

        List<StochasticTransitionFeature> processingFeatures = pdf.getStochasticTransitionFeatures();
        List<StochasticTransitionFeature> failingFeatures = pdfFailure.getStochasticTransitionFeatures();
        
        String sanitizedName = sanitizePlaceName(serviceName);
        
        Place startHappy = getStartPlace(pn, processingFeatures, sanitizedName, "");
        Place endHappy = pn.addPlace("end" + sanitizedName);
        
        Place startFail = getStartPlace(pn, failingFeatures, sanitizedName, "Fail");
        Place endFail = pn.addPlace("end" + sanitizedName + "Fail");
        
        Place endSuccess = pn.addPlace("END" + sanitizedName);
        pn.addPlace("alreadyFailed");

        Transition probHappyT = pn.addTransition("probHappy" + sanitizedName);
        probHappyT.addFeature(new Priority(prio));
        double happyWeight = 1.0 - probToFail;
        probHappyT.addFeature(StochasticTransitionFeature.newDeterministicInstance(
                BigDecimal.ZERO, MarkingExpr.of(happyWeight)));
        pn.addPrecondition(in, probHappyT);
        pn.addPostcondition(probHappyT, startHappy);

        Transition probFailT = pn.addTransition("probFail" + sanitizedName);
        probFailT.addFeature(new Priority(prio));
        probFailT.addFeature(StochasticTransitionFeature.newDeterministicInstance(
                BigDecimal.ZERO, MarkingExpr.of(probToFail)));
        probFailT.addFeature(new EnablingFunction("alreadyFailed==0"));
        probFailT.addFeature(new PostUpdater("alreadyFailed=1", pn));
        pn.addPrecondition(in, probFailT);
        pn.addPostcondition(probFailT, startFail);

        Transition tHappy = addTransitionFromTransitionFeaturesList(pn, processingFeatures, sanitizedName, startHappy, endHappy, prio, false);
        Transition tFail = addTransitionFromTransitionFeaturesList(pn, failingFeatures, sanitizedName, startFail, endFail, prio, true);

        String enablingCondition = buildEnablingFunction(sanitizedName, workflowServiceNames);
        if (!enablingCondition.isEmpty()) {
            tHappy.addFeature(new EnablingFunction(enablingCondition));
        }

        Transition toOut = pn.addTransition("toOut" + sanitizedName);
        toOut.addFeature(new Priority(prio));
        toOut.addFeature(StochasticTransitionFeature.newDeterministicInstance(BigDecimal.ZERO));
        pn.addPrecondition(endHappy, toOut);
        pn.addPostcondition(toOut, out);
        pn.addPostcondition(toOut, endSuccess);

        return prio + 2;
    }

    private static Transition addTransitionFromTransitionFeaturesList(PetriNet pn, List<StochasticTransitionFeature> features, String name, Place start, Place end, int prio, boolean fail) {
        String suffix = fail ? "Fail" : "";
        Place previous = start;

        for (int i = 0; i < features.size()-1; i++) {
            StochasticTransitionFeature feature = features.get(i);
            
            Transition t = pn.addTransition(name + suffix + String.valueOf(i));
            t.addFeature(feature);
            t.addFeature(new Priority(prio + 1 + i));
            
            pn.addPrecondition(previous, t);
            
            String postName = name + suffix + String.valueOf(i) + "intermediate";
            if (i == features.size() - 2) 
                postName = "start" + name + suffix;
            
            Place post = pn.addPlace(postName);

            pn.addPostcondition(t, post);
            previous = post;
        }

        Transition t = pn.addTransition("end" + name + suffix);
        t.addFeature(features.get(features.size()-1));
        t.addFeature(new Priority(prio + 1));
        
        pn.addPrecondition(previous, t);
        pn.addPostcondition(t, end);

        return t;
    }

    private static Place getStartPlace(PetriNet pn, List<StochasticTransitionFeature> features, String name, String suffix) {
        if (features.size() > 1)
            return pn.addPlace(name + suffix + "startIntermediate");
        else 
            return pn.addPlace("start" + name + suffix);
    }

    /**
     * Sanitizes a service name for use as a Petri Net place/transition
     * identifier by replacing hyphens, dots, and spaces with underscores.
     *
     * @param name the raw service name
     * @return the sanitized identifier
     */
    static String sanitizePlaceName(String name) {
        return name.replace("-", "_").replace(".", "_").replace(" ", "_");
    }

    /**
     * Builds the STPN enabling function expression that enforces fail-stop
     * semantics: a transition is enabled only if no other service in the
     * AND-block has failed (i.e., all {@code end<Service>Fail} places
     * contain zero tokens).
     *
     * @param currentServiceName     the sanitized name of the current service
     * @param workflowServiceNames   all service names in the AND-block
     * @return the enabling function expression (e.g., {@code "endSvc1Fail==0 && endSvc2Fail==0"}),
     *         or an empty string if there are no sibling services
     */
    static String buildEnablingFunction(String currentServiceName, List<String> workflowServiceNames) {
        if (workflowServiceNames == null || workflowServiceNames.isEmpty()) {
            return "";
        }
        List<String> otherServices = workflowServiceNames.stream()
                .filter(name -> !sanitizePlaceName(name).equals(currentServiceName))
                .collect(Collectors.toList());
        if (otherServices.isEmpty()) {
            return "";
        }
        return otherServices.stream()
                .map(name -> "end" + sanitizePlaceName(name) + "Fail==0")
                .collect(Collectors.joining("&&"));
    }

    @Override
    public double getHappyPathProbability() {
        return 1.0 - this.probToFail;
    }

    @Override
    public String workflowStringRecursive(StringBuffer sb, int level) {
        String actualIndentation = "---".repeat(level);
        String actualIndentatioWithSpaces = "   ".repeat(level);
        
        sb.append(actualIndentation).append("[SIMPLE] ").append(this.name).append("\n");
        sb.append(actualIndentatioWithSpaces).append("         ").append("ProbToFail: ").append(this.probToFail).append("\n");
        sb.append(actualIndentatioWithSpaces).append("         ").append("Mean Forward Time: ").append(this.scalableActivity.getPdf().getExpectedValue()).append("\n");
        sb.append(actualIndentatioWithSpaces).append("         ").append("Mean Compensation Time: ").append(this.compensationTime != null ? this.compensationTime.getExpectedValue() : "N/A").append("\n");
        sb.append(actualIndentatioWithSpaces).append("         ").append("Mean Failure Detection Time: ").append(this.failureTimeDistribution != null ? this.failureTimeDistribution.getExpectedValue() : "N/A").append("\n");

        return sb.toString();
    }

    @Override
    public JsonNode toJsonNode(ObjectMapper mapper) {
        ObjectNode json = mapper.createObjectNode();
        json.put("type", "SIMPLE");
        json.put("serviceName", this.getKubernetesSanitazedName());
        json.put("failingProbability", this.probToFail);
        return json;
    }

    public static void main(String[] args) {
        SagaTask task = new SimpleTask("s-1", new Simple("s-1", new VariableTruncatedExponentialTime(10, 100, 1./20.)), 0.5);

        PetriNet net = task.getStpnModel();
        XpnGenerator generator = new XpnGenerator(net);

        generator.saveToFile("single.xpn");

        Place start = net.getPlace("START");
        Marking marking = new Marking();
        marking.setTokens(start, 1);
        
        RegSteadyState analysis = RegSteadyState.builder().build();

        SteadyStateSolution<Marking> result = analysis.compute(net, marking);
        Map<Marking, BigDecimal> probs = result.getSteadyState();

        System.out.println("Steady state analysis:\n" + probs);

        RegTransient transientAnalysis = RegTransient.builder()
            .timeBound(new BigDecimal("1000"))
            .timeStep(new BigDecimal("0.1"))
            .build();

        TransientSolution<DeterministicEnablingState, Marking> resultTransient = transientAnalysis.compute(net, marking);

        System.out.println("The transient probabilities at time 999:");
        for (int j = 0; j < resultTransient.getColumnStates().size(); j++) {
            System.out.printf("%1.6f -- %s%n", resultTransient.getSolution()[9999][0][j], resultTransient.getColumnStates().get(j));
        }
    }

    @Override
    public List<List<SimpleTask>> getConcurrencyGroups() {
        List<List<SimpleTask>> concurrencyGroups = new ArrayList<>();

        List<SimpleTask> uniqueService = new ArrayList<>();
        uniqueService.add(this);
        concurrencyGroups.add(uniqueService);

        return concurrencyGroups;
    }

    public String getFailedRewardString() {
        String sanitazedName = SimpleTask.sanitizePlaceName(this.name);
        return "end" + sanitazedName + "Fail";
    }

    /**
     * Get the reward function associated with the scenario where this task is in execution.
     * In particular this function search for a path of immediate transitions that end
     * in a place of the type "*_out" that is a join place. If a path of immediate
     * transitions that reach a place of join is found then the reward is obtained by 
     * put an OR condition between the start execution place name and the join place name.
     * Otherwise the simple start place name is used. 
     * @param petriNet petri net of the entire workflow
     * @return the reward string
     */
    public String getInExecutionRewardString(PetriNet petriNet) {
        String sanitazedName = SimpleTask.sanitizePlaceName(this.name);
        String startPlaceName = "start" + sanitazedName;
        
        String endPlaceName = "end" + sanitazedName;
        Transition movingTransition = this.getTransitionFromPlace(petriNet, endPlaceName);

        while (checkIfTransitionIsDeterministicInZero(petriNet, movingTransition)) {
            String nextPlaceName = this.getNextPlace(petriNet, movingTransition).getName();
            if (nextPlaceName.contains("_out")) {
                return startPlaceName + "||(" + nextPlaceName + "&&END" + sanitazedName+ ")";
            }
            movingTransition = this.getTransitionFromPlace(petriNet, nextPlaceName);
        }

        return startPlaceName;
    }

    // Methods useful for the generation of the reward string
    // We have to search for the place with "out" in the name and put it as additional place
    // name in the rward.
    // To do this I search 
    private boolean checkIfTransitionIsDeterministicInZero(PetriNet petriNet, Transition transition) {
        StochasticTransitionFeature feature = transition.getFeature(StochasticTransitionFeature.class);

        if (feature != null && feature.density() instanceof GEN genFeature) {
            boolean isDeterministic = new XpnTransition(transition).isDeterministic(genFeature);
            if (isDeterministic && genFeature.getDomainsEFT().doubleValue() == genFeature.getDomainsLFT().doubleValue())
                return genFeature.getDomainsLFT().doubleValue() == 0.0;
        }

        return false;
    }

    private Transition getTransitionFromPlace(PetriNet petriNet, String placeName) {
        Place place = petriNet.getPlace(placeName);
        if (place == null) {
            throw new RuntimeException("Place " + placeName + " not found in the Petri Net");
        }

        return petriNet.getTransitions().stream()
                .filter(t -> petriNet.getPrecondition(place, t) != null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No transition found from place " + placeName));
    }

    private Transition getNextTransition(PetriNet petriNet, Transition transition) {
        Postcondition postCondition = this.getPostConditionFromTransition(petriNet, transition);
        Transition nextTransition = this.getTransitionFromPlace(petriNet, postCondition.getPlace().getName());

        return nextTransition;
    }

    private Place getNextPlace(PetriNet petriNet, Transition transition) {
        Postcondition postCondition = this.getPostConditionFromTransition(petriNet, transition);
        return postCondition.getPlace();
    }

    private Postcondition getPostConditionFromTransition(PetriNet petriNet, Transition transition) {
        return petriNet.getPostconditions(transition).stream()
                .filter(p -> p.getTransition().equals(transition))
                .findFirst()
                .orElse(null);
    }

    public double getSimpleTaskProbabilityToFail() {
        return this.probToFail;
    }

    public String getKubernetesSanitazedName() {
        if (this.name == null || this.name.isBlank()) {
            return "default-name";
        }

        // 1. Convert to lowercase
        // 2. Replace everything EXCEPT a-z, 0-9, and - with a hyphen
        String sanitized = this.name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-") // Removed the dot '.' from the allowed list
                .replaceAll("-+", "-")          // Collapse consecutive hyphens ("---" -> "-")
                .replaceAll("^-|-$", "");       // Trim hyphens from the start or end

        // 3. Enforce the strict 63-character limit for DNS labels (Pods/Services)
        if (sanitized.length() > 63) {
            sanitized = sanitized.substring(0, 63)
                                .replaceAll("-$", ""); // Re-trim trailing hyphen if truncated
        }

        return sanitized.isEmpty() ? "default-name" : sanitized;
    }

    public JsonNode getSimpleTasktoJson(ObjectMapper mapper){
        ObjectNode json = mapper.createObjectNode();
        json.put("name", this.getKubernetesSanitazedName());
        json.put("forward", convertDistributionToJson(mapper, this.scalableActivity.getPdf()) );
        json.put("failure", convertDistributionToJson(mapper, this.failureTimeDistribution) );
        json.put("compensation", convertDistributionToJson(mapper, this.compensationTime) );
        return json;
    }

    public JsonNode convertDistributionToJson(ObjectMapper mapper, StochasticTime distribution){
        if (distribution == null) {
            return mapper.nullNode();
        }
        ObjectNode json = mapper.createObjectNode();

        String type = distribution.getType().toString();
        json.put("type", type);

        switch (type.toUpperCase()) {
            case "DETERMINISTIC":
                json.put("value", ((DeterministicTime)distribution).getValue());
                break;
            case "GENERALIZED_ERLANG":
                GeneralizeErlangTime genErl = (GeneralizeErlangTime)distribution;
                json.put("k", genErl.getK() );
                json.put("erlangRate", genErl.getRate1() );
                json.put("exponentialRate", genErl.getRate2() );
                break;
            case "EXPONENTIAL":
                ExponentialTime exp = (ExponentialTime) distribution;
                json.put("rate", exp.getRate() );
                break;
            case "HYPER_EXPONENTIAL":
                HyperExponentialTime hyper = (HyperExponentialTime) distribution;
                json.put("lambda1", hyper.getRate1() );
                json.put("p", hyper.getProb1());
                json.put("lambda2", hyper.getRate2() );
                break;
            case "HYPO_EXPONENTIAL":
                HypoExponentialTime hypo = (HypoExponentialTime) distribution;
                json.put("rate1", hypo.getRate1() );
                json.put("rate2", hypo.getRate2() );
                break;
            case "SHIFTED_EXPONENTIAL":
                ShiftedExponentialTime shifted = (ShiftedExponentialTime) distribution;
                json.put("deterministicValue", shifted.getDeterministicValue());
                json.put("rate", shifted.getRate());
                break;
                
            default:
                throw new IllegalArgumentException("StochasticTime subtype not supported: " + type);
        }

        return json;

    }

    @Override
    public Activity composeWorkflowUntilFailure(FailCombination combination) {
        if (combination.getFailedService().equals(this))
            return this.getFailingActivity();
        if (combination.getServicesInExecution() != null && combination.getServicesInExecution().contains(this))
            return new Simple(this.name, new DeterministicTime(new BigDecimal(0)));

        return this.getForwardActivity();
    }

    @Override
    public Activity composeWorkflowFromFailure(FailCombination combination) {
        if (combination.getFailedService().equals(this))
            return new Simple(this.name, new DeterministicTime(new BigDecimal(0)));
        if (combination.getServicesInExecution().contains(this))
//            return new Simple(this.name, new DeterministicTime(new BigDecimal(0)));
            return getCompensationActivity();
        throw new IllegalStateException("SimpleTask " + this.getName() + "not present in selected Failure Combination");
    }

}
