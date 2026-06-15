package it.unifi.dinfo.stlab.variableStochasticTime;

import java.math.BigDecimal;

import org.oristool.eulero.modeling.stochastictime.TruncatedExponentialTime;

public class VariableTruncatedExponentialTime extends TruncatedExponentialTime implements VariableStochasticTime {

    private static final double RATE_ZERO_THRESHOLD = 1e-9;

    public VariableTruncatedExponentialTime(double EFT, double LFT, double rate) {
        super(EFT, LFT, rate);
    }

    @Override
    public VariableTruncatedExponentialTime cloneStochasticTime() {
        return new VariableTruncatedExponentialTime(this.getEFT().doubleValue(), this.getLFT().doubleValue(),
                this.getRate().doubleValue());
    }

    @Override
    public double calcExpectedValue() {
        if (getLFT() == getEFT()) {
            return getEFT().doubleValue();
        }
        if (getRate().doubleValue() < RATE_ZERO_THRESHOLD) {
            return getEFT().add(getLFT()).divide(BigDecimal.valueOf(2)).doubleValue();
        }
        BigDecimal intervalWidth = getLFT().subtract(getEFT());
        BigDecimal exponent = getRate().multiply(intervalWidth).negate();
        double expValue = Math.exp(exponent.doubleValue());
        BigDecimal correctionNumerator = intervalWidth.multiply(BigDecimal.valueOf(expValue));
        double correctionDenominator = 1.0 - expValue;
        BigDecimal correctionTerm = correctionNumerator.divide(BigDecimal.valueOf(correctionDenominator));
        return getEFT().add(BigDecimal.ONE.divide(getRate())).subtract(correctionTerm).doubleValue();
    }
}
