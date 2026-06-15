package it.unifi.dinfo.stlab.modeling.rearrangement;

import it.unifi.dinfo.stlab.modeling.SagaTask;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DistributionRanker {

    private final CDFComparisonDominanceComparator comparator;

    // just a utility class
    private static final class RankEntry {
        final SagaTask task;
        final double[] cdf;
        final double step;
        double points;

        RankEntry(SagaTask task, double[] cdf, double step, double points) {
            this.task = task;
            this.cdf = cdf;
            this.step = step;
            this.points = points;
        }
    }

    private final double epsilon;

    private final Map<SagaTask, RankEntry> entries = new HashMap<>();

    // headToHead.get(A).get(B) = outcome of  A vs B in {+1, 0, -1}.
    // Both directions are memorized -> more efficient for task deletion
    private final Map<SagaTask, Map<SagaTask, Double>> headToHead = new HashMap<>();

    public DistributionRanker(CDFComparisonDominanceComparator comparator) {
        this(0.001, comparator);
    }

    public DistributionRanker(double epsilon, CDFComparisonDominanceComparator comparator) {
        this.epsilon = epsilon;
        this.comparator = new CDFComparisonDominanceComparator();
    }

    public void addTask(SagaTask task, double[] cdf, double step) {
        if (entries.containsKey(task)) {
            throw new IllegalArgumentException("Task already present: " + task);
        }

        Map<SagaTask, Double> results = new HashMap<>();
        double points = 0.0;

        for (RankEntry opponent : entries.values()) {
            // Esito del NUOVO contro l'esistente, già discretizzato in {+1, 0, -1}
            double outcome = outcome(cdf, step, opponent.cdf, opponent.step);

            results.put(opponent.task, outcome);
            headToHead.get(opponent.task).put(task, -outcome);

            points += outcome;
            opponent.points -= outcome; // aggiornamento in place
        }

        headToHead.put(task, results);
        entries.put(task, new RankEntry(task, cdf, step, points));
    }

    public void removeTask(SagaTask task) {
        Map<SagaTask, Double> results = headToHead.remove(task);
        if (results == null) {
            return; // task not present
        }
        entries.remove(task);

        for (Map.Entry<SagaTask, Double> result : results.entrySet()) {
            SagaTask opponentTask = result.getKey();
            double outcome = result.getValue(); // ouctome vs the task to remove

            entries.get(opponentTask).points += outcome; // delete the contribution of the outcome
            headToHead.get(opponentTask).remove(task);
        }
    }

    public List<SagaTask> getRankedList() {
        return entries.values().stream()
                .sorted(Comparator.comparingDouble((RankEntry e) -> e.points).reversed())
                .map(e -> e.task)
                .toList();
    }

    public Map<SagaTask, Double> getRanking() {
        return entries.values().stream()
                .collect(Collectors.toMap(
                        entry -> entry.task,
                        entry -> entry.points
                ));
    }

    /**
     * Just an adapter... maybe we could use the dominance as is...
     * Adapts continuous dominance (in [0,1], where >0.5 means "the first task wins")
     * to the antisymmetric outcome {+1, 0, -1} required by the ranker.
     * Calculated only once per pair: the inverse direction is the negation.
     */
    private double outcome(double[] cdf1, double step1, double[] cdf2, double step2) {
        double d = this.comparator.dominance(cdf1, step1, cdf2, step2);
        if (d > 0.5 + epsilon) return 1.0;   // dominance of task1 over task2
        if (d < 0.5 - epsilon) return -1.0;  // dominance of task2 over task1
        return 0.0;                          // draw
    }

    public double getScore(SagaTask task){
        return entries.get(task).points;
    }

}
