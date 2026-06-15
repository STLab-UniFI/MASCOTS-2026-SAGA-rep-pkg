package it.unifi.dinfo.stlab.modeling.rearrangement;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.SagaTask;

public class FailFastEvaluator implements EvaluatorStrategy {

    @Override
    public Analysis evaluate(SagaTask task) {
        return task.analyzeTimeToFailure();
    }

    @Override
    public String name() {
        return "Fail Fast Evaluator";
    }
}
