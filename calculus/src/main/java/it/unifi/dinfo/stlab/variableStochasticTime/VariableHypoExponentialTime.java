package it.unifi.dinfo.stlab.variableStochasticTime;

import java.math.BigDecimal;

import org.oristool.eulero.modeling.stochastictime.HypoExponentialTime;

public class VariableHypoExponentialTime extends HypoExponentialTime implements VariableStochasticTime {

    public VariableHypoExponentialTime(double rate1, double rate2) {
        super(BigDecimal.valueOf(rate1), BigDecimal.valueOf(rate2));
    }

    @Override
    public VariableHypoExponentialTime cloneStochasticTime() {
        return new VariableHypoExponentialTime(this.getRate1().doubleValue(), this.getRate2().doubleValue());
    }

    @Override
    public double calcExpectedValue() {
        return this.getExpectedValue();
    }
}
