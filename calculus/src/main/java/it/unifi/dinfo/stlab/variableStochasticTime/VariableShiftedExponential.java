package it.unifi.dinfo.stlab.variableStochasticTime;

import java.math.BigDecimal;

import org.oristool.eulero.modeling.stochastictime.ShiftedExponentialTime;

public class VariableShiftedExponential extends ShiftedExponentialTime implements VariableStochasticTime{

    public VariableShiftedExponential(BigDecimal deterministicValue, BigDecimal rate) {
        super(deterministicValue, rate);
    }

    public VariableShiftedExponential(double deterministicValue, double rate) {
        super(new BigDecimal(deterministicValue), new BigDecimal(rate));
    }

    @Override
    public VariableStochasticTime cloneStochasticTime() {
        return new VariableShiftedExponential(this.getDeterministicValue(), this.getRate());
    }

    @Override
    public double calcExpectedValue() {
        return this.getExpectedValue();
    }

    @Override
    public String toString() {
        return "[SHIFT_EXP] detValue=" + this.getDeterministicValue() + ", rate=" + this.getRate();
    }
}
