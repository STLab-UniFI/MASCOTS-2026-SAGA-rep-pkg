package it.unifi.dinfo.stlab.modeling.rearrangement;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.SagaTask;

public class TimeToConsistencyEvaluator implements EvaluatorStrategy {

    @Override
    public Analysis evaluate(SagaTask task) {
        return task.analyzeTimeToConsistency();
    }

    @Override
    public String name() {
        return "Time To Consistency Evaluator";
    }
}
