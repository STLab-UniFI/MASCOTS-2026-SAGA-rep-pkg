package it.unifi.dinfo.stlab.variableStochasticTime;

import java.math.BigDecimal;

import org.oristool.eulero.modeling.stochastictime.HyperExponentialTime;

public class VariableHyperExponentialTime extends HyperExponentialTime implements VariableStochasticTime{

    public VariableHyperExponentialTime(double rate1, double rate2, double prob1) {
        super(new BigDecimal(rate1), new BigDecimal(rate2), new BigDecimal(prob1));
    }

    public VariableHyperExponentialTime(BigDecimal rate1, BigDecimal rate2, BigDecimal prob1) {
        super(rate1, rate2, prob1);
    }

    @Override
    public VariableStochasticTime cloneStochasticTime() {
        return new VariableHyperExponentialTime(this.getRate1(), this.getRate2(), this.getProb1());
    }

    @Override
    public double calcExpectedValue() {
        return this.getExpectedValue();
    }

}
