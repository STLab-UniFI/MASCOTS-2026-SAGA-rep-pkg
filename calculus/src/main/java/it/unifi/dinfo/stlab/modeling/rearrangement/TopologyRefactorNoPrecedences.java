package it.unifi.dinfo.stlab.modeling.rearrangement;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.CompositeTask;
import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.modeling.compositeTaskType.TaskEnumType;
import it.unifi.dinfo.stlab.modeling.compositeTaskType.XorTaskType;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableDeterministicTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableExponentialTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TopologyRefactorNoPrecedences {


    private static final double DOMINANCE_EPSILON = 0.01;

    EvaluatorStrategy evaluatorStrategy;

    public TopologyRefactorNoPrecedences(EvaluatorStrategy evaluatorStrategy) {
        this.evaluatorStrategy = evaluatorStrategy;
    }


    private DistributionRanker initRanking(List<SagaTask> tasks, CDFComparisonDominanceComparator comparator) {
        DistributionRanker ranker = new DistributionRanker(DOMINANCE_EPSILON, comparator);
        for (SagaTask task : tasks) {
            System.out.println("init with task:" + task.getName());
            Analysis result = this.evaluatorStrategy.evaluate(task);
            ranker.addTask(task, result.getValues(), result.getTimeStep());
        }
        return ranker;
    }

    // xor branches cannot be rearranged but their internal topology can
    public List<SagaTask> getInitialPool(SagaTask originalTopology) {
        List<SagaTask> pool = new ArrayList<>();
        collectPool(originalTopology, pool);
        return pool;
    }

    private void collectPool(SagaTask node, List<SagaTask> pool) {
        switch (node.getEnumType()) {
            case SIMPLE:
                pool.add(node);
                break;
            case XOR:
                pool.add(buildRearrangedXor(node));
                break;
            default:
                CompositeTask composite = (CompositeTask) node;
                for (SagaTask child : composite.getChildren()) {
                    collectPool(child, pool);
                }
                break;
        }
    }

    private SagaTask buildRearrangedXor(SagaTask node) {
        CompositeTask xorCast = (CompositeTask) node;
        XorTaskType xorType = (XorTaskType) xorCast.getType();
        Map<SagaTask, Double> newXorBranches = new HashMap<>();
        for (SagaTask xorChild : xorCast.getChildren()) {
            SagaTask refactoredChild = rearrange(xorChild);
            newXorBranches.put(refactoredChild, xorType.getChildrenProbability(xorChild));
        }
        return SagaTask.xor(xorCast.getName(), newXorBranches);
    }

    public SagaTask rearrange(SagaTask originalTopology) {
        List<SagaTask> initialPool = getInitialPool(originalTopology);
        CDFComparisonDominanceComparator comparator = new CDFComparisonDominanceComparator();
        DistributionRanker ranker = initRanking(initialPool, comparator);

        System.out.printf("[Rearrangement] Starting with %d pool elements: %s%n",
                ranker.getRankedList().size(),
                ranker.getRankedList().stream().map(e -> e.getName()).toList());

        // 2. Greedy merging loop
        int step = 0;
        while (ranker.getRankedList().size() > 1) {
            step++;

            List<SagaTask> rankedList = ranker.getRankedList();
            SagaTask seed = rankedList.get(0);
            SagaTask partner = rankedList.get(1);


            // Fallback: if all partners are interleaved
            if (partner == null) {
                throw new IllegalArgumentException("All the tasks are interleaved in this topology");
            }

            System.out.printf("[Step %d] Seed: %s (Copeland Score=%.6f), Partner: %s (Copeland Score=%.6f)%n",
                    step, seed.getName(), ranker.getScore(seed),
                    partner.getName(), ranker.getScore(partner));

            SagaTask seqCandidate = buildSeq(seed, partner);
            SagaTask andCandidate = buildAnd(seed, partner);

            Analysis seqEval = evaluatorStrategy.evaluate(seqCandidate);
            Analysis andEval = evaluatorStrategy.evaluate(andCandidate);
            double dominance = comparator.dominance(seqEval.getValues(), seqEval.getTimeStep(), andEval.getValues(), andEval.getTimeStep());
            boolean seqBest = dominance > (0.5 + DOMINANCE_EPSILON);

            // c. Determine merge composition
            SagaTask mergedTask;
            String mergeDesc;
            Analysis mergeResult;

            if (seqBest) {
                mergeDesc = "SEQ (no dependency, criterion chose SEQ as best pattern)";
                mergedTask = seqCandidate;
                mergeResult = evaluatorStrategy.evaluate(mergedTask);
            } else {
                mergeDesc = "AND (no dependency, criterion chose AND as best pattern)";
                mergedTask = andCandidate;
                mergeResult = evaluatorStrategy.evaluate(mergedTask);
            }

            System.out.printf("[Step %d] Merging: %s %s %s → %s%n",
                    step, seed.getName(), mergeDesc,
                    partner.getName(), mergedTask.getName());

            ranker.removeTask(seed);
            ranker.removeTask(partner);
            ranker.addTask(mergedTask, mergeResult.getValues(), mergeResult.getTimeStep());


            List<SagaTask> newRanking = ranker.getRankedList();
            System.out.printf("[Step %d] Pool (%d): %s%n",
                    step, newRanking.size(),
                    newRanking.stream().map(e -> e.getName()).toList());
        }

        // 3. Return result
        SagaTask finalElement = ranker.getRankedList().get(0);

        System.out.printf("%n[Rearrangement] ✓ Complete. Final topology: %s%n",
                finalElement.getName());

        return finalElement;
    }
    
    // ========== Task Construction ==========

    private SagaTask buildSeq(SagaTask seed, SagaTask partner) {
        SagaTask seedClone = seed.clone();
        SagaTask partnerClone = partner.clone();

        List<SagaTask> children = new ArrayList<>();
        flattenSeqChildren(seedClone, children);
        flattenSeqChildren(partnerClone, children);

        return SagaTask.seq(
                "seq(" + seed.getName() + "," + partner.getName() + ")",
                children.toArray(new SagaTask[0]));
    }


    /**
     * Builds AND(seed, partner) — both in parallel.
     */
    private SagaTask buildAnd(SagaTask seed, SagaTask partner) {
        SagaTask seedClone = seed.clone();
        SagaTask partnerClone = partner.clone();

        List<SagaTask> children = new ArrayList<>();
        flattenAndChildren(seedClone, children);
        flattenAndChildren(partnerClone, children);

        return SagaTask.and(
                "and(" + seed.getName() + "," + partner.getName() + ")",
                children.toArray(new SagaTask[0]));
    }

    private void flattenSeqChildren(SagaTask task, List<SagaTask> out) {
        if (task instanceof CompositeTask composite
                && composite.getEnumType() == TaskEnumType.SEQ) {
            out.addAll(composite.getChildren());
        } else {
            out.add(task);
        }
    }

    private void flattenAndChildren(SagaTask task, List<SagaTask> out) {
        if (task instanceof CompositeTask composite
                && composite.getEnumType() == TaskEnumType.AND) {
            out.addAll(composite.getChildren());
        } else {
            out.add(task);
        }
    }

    public static void main(String[] args) {


//        SimpleTask s1 = SagaTask.simple("s1", new VariableDeterministicTime(1), new VariableDeterministicTime(1),new VariableDeterministicTime(1) , 0.8);
//        SimpleTask s2 = SagaTask.simple("s2", new VariableDeterministicTime(2), new VariableDeterministicTime(6),new VariableDeterministicTime(6) , 0.1);
//        SimpleTask s3 = SagaTask.simple("s3", new VariableDeterministicTime(2), new VariableDeterministicTime(6),new VariableDeterministicTime(6) , 0.1);

        // s1 -> s2
        // 0.1 * det(1) + 0.1*0.9 (det1+det1)

        // s1//s2
        // 0.1*0.9 (det1) + 0.1*0.9 (det1)

        SimpleTask s1 = SagaTask.simple("s1", new VariableDeterministicTime(1), new VariableDeterministicTime(1),new VariableDeterministicTime(1),   0.9);
        SimpleTask s2 = SagaTask.simple("s2", new VariableDeterministicTime(1), new VariableDeterministicTime(1),new VariableDeterministicTime(1), 0.1);



        SimpleTask s3 = SagaTask.simple("s3", new VariableDeterministicTime(10),new VariableDeterministicTime(1),new VariableDeterministicTime(1), 0.1);
////        SimpleTask s2 = SagaTask.simple("s2", new VariableDeterministicTime(5, , 0.2);
//        SimpleTask s3 = SagaTask.simple("s3", new VariableDeterministicTime(30.), 0.1);
//        SimpleTask s4 = SagaTask.simple("s4", new VariableDeterministicTime(30.), 0.1);
//        SimpleTask s5 = SagaTask.simple("s5", new VariableDeterministicTime(30.), 0.1);
//        SimpleTask s6 = SagaTask.simple("s6", new VariableDeterministicTime(30.), 0.1);


        SimpleTask s4 = SagaTask.simple("s4", new VariableExponentialTime(1), new VariableExponentialTime(1),new VariableExponentialTime(1) , 0.8);
        SimpleTask s5 = SagaTask.simple("s5", new VariableExponentialTime(1./2.), new VariableExponentialTime(1./6.),new VariableExponentialTime(1./6.) , 0.1);
        SimpleTask s6 = SagaTask.simple("s6", new VariableExponentialTime(1./2.), new VariableExponentialTime(1./6.),new VariableExponentialTime(1./6.) , 0.1);
        CompositeTask xorand1 = SagaTask.and("xorAnd1", s4, s5, s6);

        SimpleTask s7 = SagaTask.simple("s7", new VariableExponentialTime(1), new VariableExponentialTime(1),new VariableExponentialTime(1) , 0.8);
        SimpleTask s8 = SagaTask.simple("s8", new VariableExponentialTime(1./2.), new VariableExponentialTime(1./6.),new VariableExponentialTime(1./6.) , 0.1);
        SimpleTask s9 = SagaTask.simple("s9", new VariableExponentialTime(1./2.), new VariableExponentialTime(1./6.),new VariableExponentialTime(1./6.) , 0.1);
        CompositeTask xorand2 = SagaTask.and("xorAnd2", s7, s8, s9);

        CompositeTask xor = SagaTask.xor("xor", List.of(xorand1, xorand2), List.of(0.4, 0.6));

        CompositeTask rootAnd = SagaTask.and("asdf", s1, s2, s3);

        CompositeTask rootXorAnd = SagaTask.seq("root", xor, rootAnd);

        SagaTask root = SagaTask.seq(
            "and", 
            s1, s2);

//        PetriNet stpnModel = s1.getStpnModel();
//        XpnGenerator generator = new XpnGenerator(stpnModel);
//        generator.saveToFile("./simple-net.xpn");
//        System.out.println(s1.getAllRewardCombinations());

        TopologyRefactorNoPrecedences refactor = new TopologyRefactorNoPrecedences(new TimeToConsistencyEvaluator() );
        SagaTask rearrange = refactor.rearrange(root);
        rearrange.exportTopologyJson("./rearrange-topology.json");
    }

}
