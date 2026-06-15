package it.unifi.dinfo.stlab.modeling;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.oristool.eulero.modeling.Activity;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import it.unifi.dinfo.stlab.modeling.compositeTaskType.CompositeTaskType;
import it.unifi.dinfo.stlab.modeling.utils.FailCombination;

/**
 * Internal (non-leaf) node in the SAGA task hierarchy, implementing the
 * Composite pattern.
 *
 * <p>
 * A {@code CompositeTask} groups one or more {@link SagaTask} children under
 * a specific composition operator (SEQ, AND, or XOR), defined by its
 * {@link CompositeTaskType} (Strategy pattern). All behavioral methods
 * — activity composition, CDF analysis, failure probability, compensation —
 * are delegated to the type object.
 *
 * <p>
 * The aggregate {@link #resources} field is automatically computed as the sum
 * of all children's resources.
 *
 * @see SagaTask
 * @see CompositeTaskType
 * @see it.unifi.dinfo.stlab.modeling.compositeTaskType.SeqTaskType
 * @see it.unifi.dinfo.stlab.modeling.compositeTaskType.AndTaskType
 * @see it.unifi.dinfo.stlab.modeling.compositeTaskType.XorTaskType
 */
public class CompositeTask extends SagaTask {
    @JsonManagedReference
    private CompositeTaskType type;

    /**
     * Creates a composite task with the given name and composition type.
     * The resource cost is automatically calculated from the children.
     *
     * @param name unique task name
     * @param type the composition strategy (SEQ, AND, or XOR)
     */
    public CompositeTask(String name, CompositeTaskType type) {
        super(name, type.getEnumType());
        this.setType(type);
    }

    @Override
    public Activity getForwardActivity() {
        return this.type.composeForwardActivity();
    }

    @Override
    public double[] analyze(double timeLimit, double timeTick) {
        return this.type.analyze(timeLimit, timeTick);
    }

    @Override
    public double[] analyzeWithMaximumArrayLength(int arrayLength) {
        return this.type.analyzeWithMaximumArrayLength(arrayLength);
    }

    @Override
    public double calcScalableExpectedCompletionTime() {
        return this.type.calcScalableExpectedCompletionTime();
    }

    @Override
    public CompositeTask clone() {
        CompositeTask clone = (CompositeTask) super.clone();
        CompositeTaskType clonedType = this.type.clone();
        clone.setType(clonedType);
        return clone;
    }

    public void setType(CompositeTaskType newType) {
        if (this.type == newType)
            return;
        this.type = newType;
        if (newType != null) {
            newType.setActivity(this);
        }
    }

    public CompositeTaskType getType() {
        return this.type;
    }

    public List<SagaTask> getChildren() {
        return this.type.getChildren();
    }

    @Override
    public String toString() {
        return "[" + this.type.getEnumType().toString() + "]" + this.name;
    }

    @Override
    public List<SimpleTask> getSimpleTasks() {
        return getChildren().stream().flatMap(child -> child.getSimpleTasks().stream()).collect(Collectors.toList());
    }

    @Override
    public Activity composeWorkflow(FailCombination combination) {
        if (combination.isHappyPath())
            return this.getForwardActivity();
        else 
            return this.type.composeWorkflow(combination);
    }

    @Override
    public Activity getCompensationWorkflow() {
        return this.type.getCompensationWorkflow();
    }

    @Deprecated
    @Override
    public Analysis analyzeAndSave(AnalysisType analysisType) {
        Activity activity = this.type.getActivityFromAnalysisType(analysisType);
        double timeLimit = activity.getFairTimeLimit();
        double timeStep = activity.getLeastExpectedTimeTick();
        return this.analyzeAndSave(analysisType, timeLimit, timeStep);
    }

    @Override
    public Analysis analyzeAndSave(AnalysisType analysisType, int lengthCdfArray) {
        Analysis result = this.type.analyze(analysisType, lengthCdfArray);
        this.lastAnalysis.put(analysisType, result);
        return result;
    }

    @Override
    public Analysis analyzeAndSave(AnalysisType analysisType, double timeLimit, double timeStep) {
        Analysis result = this.type.analyze(analysisType, timeLimit, timeStep);
        this.lastAnalysis.put(analysisType, result);
        return result;
    }

    @Override
    public Activity getActivityWithFailure(List<String> workflowServiceNames) {
        return this.type.composeActivityWithFailure(workflowServiceNames);
    }

    @Override
    public double getHappyPathProbability() {
        return this.type.getHappyPathProbability();
    }

    @Override
    public String workflowStringRecursive(StringBuffer sb, int level) {
        String actualIndentation = "---".repeat(level);
        sb.append(actualIndentation).append("[" + this.type.getEnumType() + "] ").append(this.name).append("\n");
        for (SagaTask child : this.getChildren()) {
            child.workflowStringRecursive(sb, level + 1);
        }
        return sb.toString();
    }

    @Override
    public JsonNode toJsonNode(ObjectMapper mapper) {
        return this.type.toJsonNode(mapper);
    }

    @Override
    public List<List<SimpleTask>> getConcurrencyGroups() {
        return this.type.getConcurrencyGroups();
    }

    @Override
    public boolean containsFailCombination(FailCombination combination){
        List<SimpleTask> simpleTasks = this.getSimpleTasks();
        SimpleTask failedService = combination.getFailedService();
        List<SimpleTask> servicesInExecution = combination.getServicesInExecution();
        return simpleTasks.stream().anyMatch(task -> {
            boolean isFailed = task.equals(failedService);
            boolean isInExecution = (servicesInExecution != null && servicesInExecution.contains(task));

            return isFailed || isInExecution;
        });
    }

    @Override
    public Activity composeWorkflowUntilFailure(FailCombination combination) {
        return this.type.composeWorkflowUntilFailure(combination);
    }

    @Override
    public Activity composeWorkflowFromFailure(FailCombination combination) {
        return this.type.composeWorkflowFromFailure(combination);
    }

//    @Override
//    public Map<SimpleTask, String> getAllSyncRewards() {
//        return this.type.getAllSyncRewards();
//    }
}
