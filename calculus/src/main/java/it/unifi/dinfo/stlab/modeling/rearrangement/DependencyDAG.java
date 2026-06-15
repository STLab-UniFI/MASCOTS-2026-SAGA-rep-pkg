package it.unifi.dinfo.stlab.modeling.rearrangement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unifi.dinfo.stlab.modeling.CompositeTask;
import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.modeling.compositeTaskType.TaskEnumType;

/**
 * Tracks dependency constraints (partial order) between leaf services
 * in a workflow topology.
 *
 * <p>
 * Services arranged sequentially have a "must-precede" constraint.
 * Services arranged in parallel (AND) have no ordering constraint.
 * The DAG stores the <b>transitive closure</b> of the precedence relation,
 * enabling O(1) precedence queries between any pair of leaf services.
 * </p>
 *
 * <h3>Usage</h3>
 * 
 * <pre>{@code
 * DependencyDAG dag = DependencyDAG.fromTaskTree(root);
 * boolean mustOrder = dag.precedes("s-1", "s-2"); // true if s-1 must run before s-2
 * boolean independent = !dag.anyPrecedes(setA, setB) && !dag.anyPrecedes(setB, setA);
 * }</pre>
 */
public class DependencyDAG {

    // precedes.get(A).contains(B) ⟺ leaf service A must execute before leaf service
    private final Map<String, Set<String>> precedes;

    private DependencyDAG() {
        this.precedes = new HashMap<>();
    }

    /**
     * Builds a DependencyDAG from a SagaTask tree.
     *
     * <p>
     * Extracts ordering constraints from SEQ compositions (children must
     * execute in order) and records no constraints for AND compositions
     * (children are independent). Computes the transitive closure.
     * </p>
     *
     * @param root the root of the task tree
     * @return a DependencyDAG with all transitive precedence relationships
     */
    public static DependencyDAG fromTaskTree(SagaTask root) {
        DependencyDAG dag = new DependencyDAG();

        // Register all leaf services
        for (SimpleTask leaf : root.getSimpleTasks()) {
            dag.precedes.computeIfAbsent(leaf.getName(), k -> new HashSet<>());
        }

        // Extract direct dependencies from SEQ compositions
        extractDirectDeps(root, dag);

        // Compute transitive closure
        dag.computeTransitiveClosure();

        return dag;
    }

    /**
     * Returns true if leaf service {@code a} must execute before leaf service
     * {@code b}.
     */
    public boolean precedes(String a, String b) {
        Set<String> succs = precedes.get(a);
        return succs != null && succs.contains(b);
    }

    /**
     * Returns true if any leaf in {@code leavesA} must precede any leaf in
     * {@code leavesB}.
     */
    public boolean anyPrecedes(Collection<String> leavesA, Collection<String> leavesB) {
        for (String a : leavesA) {
            for (String b : leavesB) {
                if (precedes(a, b))
                    return true;
            }
        }
        return false;
    }

    public boolean anyTaskPrecedes(Collection<SimpleTask> leavesA, Collection<SimpleTask> leavesB) {
        for (SagaTask a : leavesA) {
            for (SagaTask b : leavesB) {
                if (precedes(a.getName(), b.getName()))
                    return true;
            }
        }
        return false;
    }

    /**
     * Returns the set of all known leaf service names.
     */
    public Set<String> getAllLeafNames() {
        return Collections.unmodifiableSet(precedes.keySet());
    }

    /**
     * Returns the <b>direct</b> (depth-1) dependents of {@code sourceLeaves}
     * that are present in {@code candidateSet}.
     *
     * <p>
     * A leaf D is a direct dependent of source S if:
     * <ul>
     * <li>S precedes D (in the transitive closure)</li>
     * <li>There is no intermediate leaf I in {@code candidateSet}
     * such that S ≺ I ≺ D</li>
     * </ul>
     *
     * <p>
     * Used for depth-1 look-ahead: when evaluating a merge partner,
     * we augment it with only its immediate next dependent, avoiding
     * the bias that a full transitive chain would introduce.
     * </p>
     *
     * @param sourceLeaves the leaves whose direct dependents we want
     * @param candidateSet only dependents within this set are considered
     * @return set of direct dependent leaf names (typically 0 or 1)
     */
    public Set<String> getDirectDependents(
            Collection<String> sourceLeaves, Set<String> candidateSet) {
        Set<String> result = new HashSet<>();
        for (String src : sourceLeaves) {
            Set<String> succs = precedes.get(src);
            if (succs == null)
                continue;
            for (String candidate : candidateSet) {
                if (!succs.contains(candidate))
                    continue;
                // Check: is there an intermediate node in candidateSet between src and
                // candidate?
                boolean hasIntermediate = false;
                for (String mid : candidateSet) {
                    if (mid.equals(candidate))
                        continue;
                    if (precedes(src, mid) && precedes(mid, candidate)) {
                        hasIntermediate = true;
                        break;
                    }
                }
                if (!hasIntermediate) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    /**
     * Prints the dependency DAG to stdout for debugging.
     */
    public void printDependencies() {
        System.out.println("[DependencyDAG] Precedence constraints (transitive closure):");
        for (Map.Entry<String, Set<String>> entry : precedes.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                System.out.printf("  %s ≺ %s%n", entry.getKey(), entry.getValue());
            }
        }
    }

    /*
     * Extracts direct dependencies from a task tree in a recursive manner.
     */
    private static void extractDirectDeps(SagaTask task, DependencyDAG dag) {
        if (task instanceof SimpleTask) {
            return; // Leaf: no internal dependencies
        }

        if (task instanceof CompositeTask composite) {
            List<SagaTask> children = composite.getChildren();

            if (composite.getType().getEnumType() == TaskEnumType.SEQ) {
                // Sequential: each child's leaves must precede the next child's leaves
                for (int i = 0; i < children.size() - 1; i++) {
                    List<String> leftLeaves = getLeafNames(children.get(i));
                    List<String> rightLeaves = getLeafNames(children.get(i + 1));
                    for (String l : leftLeaves) {
                        for (String r : rightLeaves) {
                            dag.precedes.computeIfAbsent(l, k -> new HashSet<>()).add(r);
                        }
                    }
                }
            }
            // AND/XOR: no cross-child ordering constraints

            // Recurse into all children
            for (SagaTask child : children) {
                extractDirectDeps(child, dag);
            }
        }
    }

    private static List<String> getLeafNames(SagaTask task) {
        return task.getSimpleTasks().stream()
                .map(SimpleTask::getName)
                .toList();
    }

    /*
     * Computes the transitive closure of the precedence relation.
     * In other words, it adds all the missing dependencies to the graph.
     */
    private void computeTransitiveClosure() {
        List<String> nodes = new ArrayList<>(precedes.keySet());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String a : nodes) {
                Set<String> aSucc = precedes.get(a);
                if (aSucc == null)
                    continue;
                for (String b : new ArrayList<>(aSucc)) {
                    Set<String> bSucc = precedes.get(b);
                    if (bSucc == null)
                        continue;
                    for (String c : bSucc) {
                        if (aSucc.add(c)) {
                            changed = true;
                        }
                    }
                }
            }
        }
    }
}
