package it.unifi.dinfo.stlab.modeling.compositeTaskType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
 * XOR (exclusive choice) composition operator for the SAGA task hierarchy.
 *
 * <p>
 * Exactly one child branch is executed, selected probabilistically at
 * runtime according to {@link #branchProbabilities}. The composed Eulero
 * {@link Activity} is a
 * {@link org.oristool.eulero.modeling.ModelFactory#XOR XOR} with the
 * branch probabilities as weights, so the CDF of the total time is the
 * weighted mixture of the children's CDFs.
 *
 * <h3>Topology-Aware Behavior</h3>
 * <p>
 * For scenario generation, XOR nodes require a deterministic branch
 * selection via the {@code xorChoice} map (XOR node name → chosen branch
 * index). All topology-aware methods (reachability, compensation,
 * failure probability) operate only on the chosen branch.
 *
 * <h3>Compensation Semantics</h3>
 * <p>
 * Only the chosen branch is compensated. The compensation activity is
 * a XOR over the branches, weighted by their selection probabilities.
 *
 * @see CompositeTaskType
 * @see it.unifi.dinfo.stlab.modeling.compositeTaskType.SeqTaskType
 * @see it.unifi.dinfo.stlab.modeling.compositeTaskType.AndTaskType
 */
public class XorTaskType extends CompositeTaskType {

    /** Maps each child branch to its selection probability (must sum to 1.0). */
    Map<SagaTask, Double> branchProbabilities;

    /**
     * Creates a XOR composition with the given children and their
     * selection probabilities.
     *
     * @param children      the child branches
     * @param probabilities selection probability for each branch (same order as children)
     * @throws IllegalArgumentException if children and probabilities have different sizes
     */
    public XorTaskType(List<SagaTask> children, List<Double> probabilities) {
        if (children.size() != probabilities.size())
            throw new IllegalArgumentException(
                    "Children and probabilities should be of the same size. " +
                            "Children: " + children.size() + ", Probabilities: " + probabilities.size());

        branchProbabilities = IntStream.range(0, children.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> children.get(i),
                        i -> probabilities.get(i),
                        (a, b) -> a,
                        LinkedHashMap::new));
        this.children = children;
    }

    /**
     * Creates a XOR composition from a pre-built probability map.
     *
     * @param branchProbabilities map of child branches to selection probabilities
     */
    public XorTaskType(Map<SagaTask, Double> branchProbabilities) {
        this.branchProbabilities = branchProbabilities;
        this.children = new ArrayList<>(branchProbabilities.keySet());
    }

    @Override
    public TaskEnumType getEnumType() {
        return TaskEnumType.XOR;
    }

    public List<Double> getBranchProbabilities() {
        return this.children.stream()
                .map(child -> branchProbabilities.getOrDefault(child, 0.0))
                .collect(Collectors.toList());
    }

    @Override
    public Activity composeForwardActivity() {
        List<Double> probabilities = new ArrayList<>();
        List<Activity> childrenActivities = new ArrayList<>();

        for (SagaTask child : this.children) {
            probabilities.add(this.branchProbabilities.get(child));
            childrenActivities.add(child.getForwardActivity());
        }

        return ModelFactory.XOR(probabilities, childrenActivities.toArray(Activity[]::new));
    }

    public double getChildrenProbability(SagaTask children) {
        return this.branchProbabilities.get(children);
    }

    public ArrayList<Double> getProbabilities() {
        ArrayList<Double> probabilities = new ArrayList<>();
        for (SagaTask task : this.children) {
            probabilities.add(this.branchProbabilities.get(task));
        }
        return probabilities;
    }

    public double getProbability(SagaTask task) {
        if (this.branchProbabilities.keySet().contains(task)) {
            return this.branchProbabilities.get(task);
        }
        throw new RuntimeException("No probability for " + task.toString());
    }

    @Override
    public XorTaskType clone() {
        XorTaskType clone = (XorTaskType) super.clone();
        if (this.branchProbabilities != null) {
            clone.branchProbabilities = new LinkedHashMap<>();
            for (Map.Entry<SagaTask, Double> entry : this.branchProbabilities.entrySet()) {
                SagaTask originalTask = entry.getKey();
                Double probability = entry.getValue();
                int index = this.children.indexOf(originalTask);
                if (index == -1) {
                    throw new AssertionError("Inconsistency Error");
                }
                SagaTask clonedTask = clone.children.get(index);
                clone.branchProbabilities.put(clonedTask, probability);
            }
        }
        return clone;
    }

    @Override
    public Activity composeWorkflow(FailCombination combination) {
        if(!this.getActivity().containsFailCombination(combination))
            return composeForwardActivity();
        else{
            SagaTask executionBranch = getExecutionBranch(combination);
            return executionBranch.composeWorkflow(combination);
        }
    }

    private SagaTask getExecutionBranch(FailCombination combination){
        return children.stream().filter(child->child.containsFailCombination(combination)).findFirst().orElse(null);
    }

    @Override
    public Activity getCompensationWorkflow() {
        return ModelFactory.XOR(getBranchProbabilities(), this.children
                .stream().map(SagaTask::getCompensationWorkflow).toArray(Activity[]::new));
    }

    @Override
    public JsonNode toJsonNode(ObjectMapper mapper) {
        ObjectNode json = mapper.createObjectNode();
        json.put("type", "XOR");

        ArrayNode branchesArray = mapper.createArrayNode();

        for (Map.Entry<SagaTask, Double> entry : branchProbabilities.entrySet()) {
            ObjectNode singleBranchJson = mapper.createObjectNode();
            singleBranchJson.set("branch", entry.getKey().toJsonNode(mapper));
            singleBranchJson.put("probability", entry.getValue());
            branchesArray.add(singleBranchJson);
        }
        json.set("branches", branchesArray);
        return json;
    }

    @Override
    public Activity composeActivityWithFailure(List<String> workflowServiceNames) {
        return ModelFactory.XOR(getBranchProbabilities(), this.children
                .stream().map(child -> child.getActivityWithFailure(workflowServiceNames)).toArray(Activity[]::new));
    }

    @Override
    public double getHappyPathProbability() {
        double prob = 0.0;

        for (SagaTask child : this.getChildren()) {
            prob += this.branchProbabilities.get(child) * child.getHappyPathProbability();
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

    public Activity composeWorkflowUntilFailure(FailCombination combination) {
        SagaTask targetTask = this.getExecutionBranch(combination);

        // In this case the XOR block doesn't contain the fail combination
        // So it should return the forward activity only
        if (targetTask == null){
            return this.composeForwardActivity();
        }
        // Otherwise I need to call the composeWorkflowUntilFailure method in the target task only
        // because that branch is the branch chosen
        else {
            return targetTask.composeWorkflowUntilFailure(combination);
        }
    }

    @Override
    public Activity composeWorkflowFromFailure(FailCombination combination) {
        if(!this.getActivity().containsFailCombination(combination))
            throw new IllegalStateException("Xor block should contain fail combination");
//            return new Simple("placeholder_" + this.toString(), new DeterministicTime(BigDecimal.valueOf(0)));
        else{
            SagaTask executionBranch = getExecutionBranch(combination);
            return executionBranch.composeWorkflowFromFailure(combination);
        }
    }
}
