package it.unifi.dinfo.stlab.modeling.rearrangement;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.SagaTask;

public class CompensateFastEvaluator implements EvaluatorStrategy {

    @Override
    public Analysis evaluate(SagaTask task) {
        return task.analizeTimeFromFailure();
    }

    @Override
    public String name() {
        return "Compensate Fast Evaluator";
    }

    
}
