package it.unifi.dinfo.stlab.variableStochasticTime;

import org.oristool.eulero.modeling.stochastictime.ErlangTime;

public class VariableErlangTime extends ErlangTime implements VariableStochasticTime {

    public VariableErlangTime(int k, double rate) {
        super(k, rate);
    }

    @Override
    public VariableErlangTime cloneStochasticTime() {
        return new VariableErlangTime(this.getK(), this.getRate());
    }

    @Override
    public double calcExpectedValue() {
        return this.getK() / this.getRate();
    }
}
