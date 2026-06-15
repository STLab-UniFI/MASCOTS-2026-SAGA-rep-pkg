package it.unifi.dinfo.stlab.variableStochasticTime;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.oristool.eulero.modeling.stochastictime.GeneralizeErlangTime;

public class VariableGeneralizedErlangTime extends GeneralizeErlangTime implements VariableStochasticTime{

    public VariableGeneralizedErlangTime(int k, double rate1, double rate2) {
        super(k, new BigDecimal(rate1), new BigDecimal(rate2));
    }

    public VariableGeneralizedErlangTime(int k, BigDecimal rate1, BigDecimal rate2) {
        super(k, rate1, rate2);
    }

    @Override
    public VariableStochasticTime cloneStochasticTime() {
        return new VariableGeneralizedErlangTime(this.getK(), this.getRate1(), this.getRate2());
    }

    @Override
    public double calcExpectedValue() {
        return this.getExpectedValue();
    }

    @Override
    public String toString() {
        return "[GEN_ERL] k=" + this.getK() + ", rate1=" + this.getRate1() + ", rate2=" + this.getRate2() + ", CV=" + this.getCoefficientOfVariation();  
    }

    public double getCoefficientOfVariation() {
        return Math.sqrt(this.getVariance())/this.getExpectedValue();
    }

    public VariableShiftedExponential convertToShiftedExponential() {
        BigDecimal sqrtVariance = new BigDecimal(this.getVariance()).sqrt(MathContext.DECIMAL128);

        BigDecimal newRate = BigDecimal.ONE.divide(sqrtVariance, MathContext.DECIMAL128);
        
        BigDecimal newDeterministicValue = new BigDecimal(this.getExpectedValue()).subtract(sqrtVariance);

        return new VariableShiftedExponential(newDeterministicValue, newRate);
    }
}
