package it.unifi.dinfo.stlab.modeling.rearrangement;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.SagaTask;

public interface EvaluatorStrategy {

    public Analysis evaluate(SagaTask task);

    public String name();

}
