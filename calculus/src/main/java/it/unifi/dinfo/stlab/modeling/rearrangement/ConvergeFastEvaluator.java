package it.unifi.dinfo.stlab.modeling.rearrangement;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.AnalysisType;
import it.unifi.dinfo.stlab.modeling.Constants;
import it.unifi.dinfo.stlab.modeling.SagaTask;

public class ConvergeFastEvaluator implements EvaluatorStrategy{

    @Override
    public Analysis evaluate(SagaTask task) {
        return task.analyzeAndSave(AnalysisType.FORWARD, Constants.RECOMMENDED_ANALYSIS_ARRAY_LENGTH);
    }

    @Override
    public String name() {
        return "Converge Fast Evaluator";
    }

}
