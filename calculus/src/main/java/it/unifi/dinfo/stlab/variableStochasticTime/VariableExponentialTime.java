package it.unifi.dinfo.stlab.variableStochasticTime;

import java.math.BigDecimal;

import org.oristool.eulero.modeling.stochastictime.ExponentialTime;

public class VariableExponentialTime extends ExponentialTime implements VariableStochasticTime {

    public VariableExponentialTime(double rate) {
        super(BigDecimal.valueOf(rate));
    }

    @Override
    public VariableExponentialTime cloneStochasticTime() {
        return new VariableExponentialTime(this.getRate().doubleValue());
    }

    @Override
    public double calcExpectedValue() {
        return 1 / this.getRate().doubleValue();
    }
}
