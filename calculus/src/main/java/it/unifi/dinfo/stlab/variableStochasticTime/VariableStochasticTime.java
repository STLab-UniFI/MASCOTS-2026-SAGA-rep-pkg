package it.unifi.dinfo.stlab.variableStochasticTime;

/**
 * Interface for stochastic time distributions with cloning and expected value
 * support.
 */
public interface VariableStochasticTime {
    public VariableStochasticTime cloneStochasticTime();

    public double calcExpectedValue();
}
