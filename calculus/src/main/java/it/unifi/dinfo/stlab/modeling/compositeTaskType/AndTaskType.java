package it.unifi.dinfo.stlab.modeling.compositeTaskType;

import java.util.ArrayList;
import java.util.List;

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
 * AND (parallel fork-join) composition operator for the SAGA task hierarchy.
 *
 * <p>
 * All children are executed concurrently; the composed Eulero {@link Activity}
 * is a {@link org.oristool.eulero.modeling.ModelFactory#forkJoin forkJoin},
 * completing when the slowest branch finishes. The CDF of the total time
 * is thus the product of the individual CDFs (maximum of independent
 * random variables).
 *
 * <h3>STPN-Based Scenario Enumeration</h3>
 * <p>
 * When a task in one branch fails, the state of sibling branches at the
 * moment of failure is non-trivial because branches run concurrently.
 * This class uses {@link ANDScenarioEnumerator} and
 * {@link it.unifi.dinfo.stlab.modeling.stpn.ANDBlockSTPNBuilder} to
 * compute the exact joint probability of each possible combination of
 * branch states via transient analysis of the STPN model.
 *
 * <h3>Compensation Semantics</h3>
 * <p>
 * Compensation respects parallelism: branches are compensated
 * concurrently ({@code forkJoin}), while within each branch the
 * compensation proceeds sequentially in reverse order. The failing
 * branch receives partial compensation (only committed tasks before
 * the failure point), while sibling branches are fully compensated.
 *
 * @see CompositeTaskType
 * @see ANDScenarioEnumerator
 * @see it.unifi.dinfo.stlab.modeling.stpn.ANDBlockSTPNBuilder
 * @see ANDExecutionScenario
 */
public class AndTaskType extends CompositeTaskType {
    /**
     * Creates an AND composition with the given children (parallel branches).
     *
     * @param children the tasks to execute concurrently
     */
    public AndTaskType(List<SagaTask> children) {
        this.children = children;
    }

    @Override
    public TaskEnumType getEnumType() {
        return TaskEnumType.AND;
    }

    @Override
    public Activity composeForwardActivity() {
        return ModelFactory
                .forkJoin(this.children.stream().map(SagaTask::getForwardActivity).toArray(Activity[]::new));
    }

    @Override
    public AndTaskType clone() {
        return (AndTaskType) super.clone();
    }

    @Override
    public Activity composeWorkflow(FailCombination combination) {
        if (!this.getActivity().containsFailCombination(combination))
            return composeForwardActivity();
        else
            return ModelFactory.forkJoin(this.children.stream().map(child -> child.composeWorkflow(combination)).toArray(Activity[]::new));
    }

    @Override
    public Activity getCompensationWorkflow() {
        return ModelFactory.forkJoin(this.children.stream().map(SagaTask::getCompensationWorkflow).toArray(Activity[]::new));
    }

    @Override
    public JsonNode toJsonNode(ObjectMapper mapper) {
        ObjectNode json = mapper.createObjectNode();
        json.put("type", "AND");

        ArrayNode jsonChildren = mapper.createArrayNode();
        for (SagaTask child : this.children) {
            jsonChildren.add(child.toJsonNode(mapper));
        }

        json.set("children", jsonChildren);
        return json;
    }

    @Override
    public Activity composeActivityWithFailure(List<String> workflowServiceNames) {
        return ModelFactory.forkJoin(this.children.stream()
                .map(child -> child.getActivityWithFailure(workflowServiceNames)).toArray(Activity[]::new));
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
        List<List<List<SimpleTask>>> allGroups = new ArrayList<>();

        for (SagaTask child : this.getChildren()) {
            List<List<SimpleTask>> innerList = child.getConcurrencyGroups();
            allGroups.add(innerList);
        }

        List<List<SimpleTask>> result = this.calculateConcurrencyGroupCombinations(allGroups);

        return result;
    }

    private List<List<SimpleTask>> calculateConcurrencyGroupCombinations(List<List<List<SimpleTask>>> allGroups) {
        List<List<SimpleTask>> results = new ArrayList<>();
        this.generateCombinations(allGroups, 0, new ArrayList<>(), results);

        return results;
    }

    private void generateCombinations(
            List<List<List<SimpleTask>>> groups,
            int groupIndex,
            List<SimpleTask> currentPath,
            List<List<SimpleTask>> results) {

        // Base case: If we've processed all groups, add the current path to results
        if (groupIndex == groups.size()) {
            results.add(new ArrayList<>(currentPath));
            return;
        }

        // Get the current group (e.g., Group 1: [(1,2), (3)])
        List<List<SimpleTask>> currentGroup = groups.get(groupIndex);

        // Iterate through each option in the group
        for (List<SimpleTask> option : currentGroup) {
            // Add all elements of the chosen option
            currentPath.addAll(option);

            // Recurse to the next group
            generateCombinations(groups, groupIndex + 1, currentPath, results);

            // Backtrack: Remove the elements we just added to try the next option
            for (int i = 0; i < option.size(); i++) {
                currentPath.remove(currentPath.size() - 1);
            }
        }
    }

    @Override
    public Activity composeWorkflowUntilFailure(FailCombination combination) {
        List<Activity> composedActivities = new ArrayList<>();

        for (SagaTask child : this.children)
            composedActivities.add(child.composeWorkflowUntilFailure(combination));

        return ModelFactory.forkJoin(
            composedActivities.toArray(new Activity[0])
        );
    }

    @Override
    public Activity composeWorkflowFromFailure(FailCombination combination) {
        if (!this.getActivity().containsFailCombination(combination))
//            return new Simple("placeholder_" + this.toString(), new DeterministicTime(BigDecimal.valueOf(0)));
            throw new IllegalStateException("And block should contain fail combination");
        else
            return ModelFactory.forkJoin(this.children.stream().map(child -> child.composeWorkflowFromFailure(combination)).toArray(Activity[]::new));
    }
}
