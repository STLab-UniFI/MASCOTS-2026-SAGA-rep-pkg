package it.unifi.dinfo.stlab.variableStochasticTime;

import java.math.BigDecimal;

import org.oristool.eulero.modeling.stochastictime.DeterministicTime;

public class VariableDeterministicTime extends DeterministicTime implements VariableStochasticTime {

    public VariableDeterministicTime(double value) {
        super(BigDecimal.valueOf(value));
    }

    @Override
    public VariableDeterministicTime cloneStochasticTime() {
        return new VariableDeterministicTime(this.getValue().doubleValue());
    }

    @Override
    public double calcExpectedValue() {
        return this.getValue().doubleValue();
    }
}
