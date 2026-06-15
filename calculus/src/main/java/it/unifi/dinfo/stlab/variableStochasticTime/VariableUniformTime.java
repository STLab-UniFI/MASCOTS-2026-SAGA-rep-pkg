package it.unifi.dinfo.stlab.variableStochasticTime;

import java.math.BigDecimal;

import org.oristool.eulero.modeling.stochastictime.UniformTime;

public class VariableUniformTime extends UniformTime implements VariableStochasticTime {

    public VariableUniformTime(double EFT, double LFT) {
        super(EFT, LFT);
    }

    @Override
    public VariableUniformTime cloneStochasticTime() {
        return new VariableUniformTime(this.getEFT().doubleValue(), this.getLFT().doubleValue());
    }

    @Override
    public double calcExpectedValue() {
        return getEFT().add(getLFT()).divide(BigDecimal.valueOf(2)).doubleValue();
    }
}
