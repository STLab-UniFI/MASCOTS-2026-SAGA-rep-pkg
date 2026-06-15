package it.unifi.dinfo.stlab.modeling.compositeTaskType;

import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.oristool.eulero.modeling.Activity;
import org.oristool.eulero.modeling.ModelFactory;

import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.modeling.utils.FailCombination;

/**
 * SEQ (sequential) composition operator for the SAGA task hierarchy.
 *
 * <p>
 * Children are executed in strict order: child <em>i+1</em> starts only
 * after child <em>i</em> completes. The composed Eulero {@link Activity}
 * is a {@link org.oristool.eulero.modeling.ModelFactory#sequence sequence}
 * of the children activities, and the CDF of the total time is the
 * convolution of the individual CDFs.
 *
 * <h3>Compensation Semantics</h3>
 * <p>
 * When a task at position <em>i</em> fails, all previously committed
 * tasks <em>0..i−1</em> are compensated in <b>reverse</b> execution order.
 * The {@link #composeCompensationActivity} method models this as a
 * probabilistic XOR over all possible failure points, where each branch
 * contains the sequential compensation of the committed tasks.
 *
 * @see CompositeTaskType
 * @see it.unifi.dinfo.stlab.modeling.compositeTaskType.AndTaskType
 * @see it.unifi.dinfo.stlab.modeling.compositeTaskType.XorTaskType
 */
public class SeqTaskType extends CompositeTaskType {

    /**
     * Creates a SEQ composition with the given children.
     *
     * @param children the tasks to execute sequentially
     */
    public SeqTaskType(List<SagaTask> children) {
        this.children = children;
    }

    @Override
    public TaskEnumType getEnumType() {
        return TaskEnumType.SEQ;
    }

    @Override
    public Activity composeForwardActivity() {
        return ModelFactory
                .sequence(this.children.stream().map(SagaTask::getForwardActivity).toArray(Activity[]::new));
    }

    @Override
    public SeqTaskType clone() {
        return (SeqTaskType) super.clone();
    }

    @Override
    public Activity composeWorkflow(FailCombination combination) {
        List<SagaTask> children = this.getChildren();
        List<Activity> sequentialActivities = new ArrayList<>();
        Deque<SagaTask> visitedChildren = new ArrayDeque<>();
        for (SagaTask child : children) {

            if (child.containsFailCombination(combination)) {
                sequentialActivities.add(child.composeWorkflow(combination));
                break;
            }
            sequentialActivities.add(child.getForwardActivity());
            visitedChildren.push(child);
        }

        while (!visitedChildren.isEmpty()) {
            SagaTask child = visitedChildren.pop();
            sequentialActivities.add(child.getCompensationWorkflow());
        }
        return ModelFactory.sequence(sequentialActivities.toArray(Activity[]::new));
    }

    @Override
    public Activity getCompensationWorkflow() {
        List<Activity> sequentialActivities = new ArrayList<>();
        for (int i = children.size() - 1; i >= 0; i--) {
            sequentialActivities.add(children.get(i).getCompensationWorkflow());
        }
        return ModelFactory.sequence(sequentialActivities.toArray(Activity[]::new));
    }

    @Override
    public JsonNode toJsonNode(ObjectMapper mapper) {
        ObjectNode json = mapper.createObjectNode();
        json.put("type", "SEQ");

        ArrayNode jsonChildren = mapper.createArrayNode();
        for (SagaTask child : this.children) {
            jsonChildren.add(child.toJsonNode(mapper));
        }

        json.set("children", jsonChildren);
        return json;
    }

    @Override
    public Activity composeActivityWithFailure(List<String> workflowServiceNames) {
        return ModelFactory
                .sequence(this.children.stream().map(child -> child.getActivityWithFailure(workflowServiceNames))
                        .toArray(Activity[]::new));
    }

    @Override
    public double getHappyPathProbability() {
        double prob = 1.0;

        for (SagaTask child : this.getChildren()) {
            prob *= child.getHappyPathProbability();
        }

        return prob;
    }

    @Override
    public List<List<SimpleTask>> getConcurrencyGroups() {
        List<List<SimpleTask>> concurrencyGroups = new ArrayList<>();

        for (SagaTask child : this.getChildren()) {
            List<List<SimpleTask>> innerList = child.getConcurrencyGroups();
            concurrencyGroups.addAll(innerList);
        }

        return concurrencyGroups;
    }

    @Override
    public Activity composeWorkflowUntilFailure(FailCombination combination) {
        List<Activity> composedChildren = new ArrayList<>();

        boolean found = false;
        int i = 0;
        while (!found && i < this.children.size()) {
            SagaTask child = this.children.get(i);
            if (child.containsFailCombination(combination))
                found = true;
            composedChildren.add(child.composeWorkflowUntilFailure(combination));
            i++;
        }

        // at the end of the while loop if some child contains the fail combination
        // the composedChildren contains all the children until it
        // otherwise the composed children contains all the forward pass for 
        // all the children

        return ModelFactory.sequence(
            composedChildren.toArray(new Activity[0])
        );
    }

    @Override
    public Activity composeWorkflowFromFailure(FailCombination combination) {
        List<SagaTask> children = this.getChildren();
        List<Activity> sequentialActivities = new ArrayList<>();
        Deque<SagaTask> visitedChildren = new ArrayDeque<>();
        for (SagaTask child : children) {

            if (child.containsFailCombination(combination)) {
                sequentialActivities.add(child.composeWorkflowFromFailure(combination));
                break;
            }
            visitedChildren.push(child);
        }

        while (!visitedChildren.isEmpty()) {
            SagaTask child = visitedChildren.pop();
            sequentialActivities.add(child.getCompensationWorkflow());
        }
        return ModelFactory.sequence(sequentialActivities.toArray(Activity[]::new));
    }
}
