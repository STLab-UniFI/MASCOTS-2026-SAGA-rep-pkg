package it.unifi.dinfo.stlab.variableStochasticTime;

import org.oristool.eulero.modeling.stochastictime.DeterministicTime;
import org.oristool.eulero.modeling.stochastictime.ExponentialTime;
import org.oristool.eulero.modeling.stochastictime.HypoExponentialTime;
import org.oristool.eulero.modeling.stochastictime.StochasticTime;
import org.oristool.eulero.modeling.stochastictime.TruncatedExponentialTime;
import org.oristool.eulero.modeling.stochastictime.UniformTime;

public interface VariableStochasticTimeCaster {

    public static VariableStochasticTime getVariableStochasticTime(StochasticTime stochasticTime) {
        if (stochasticTime instanceof TruncatedExponentialTime) {
            return VariableStochasticTimeCaster.getVariableStochasticTime((TruncatedExponentialTime) stochasticTime);
        }
        if (stochasticTime instanceof HypoExponentialTime) {
            return VariableStochasticTimeCaster.getVariableStochasticTime((HypoExponentialTime) stochasticTime);
        }
        if (stochasticTime instanceof DeterministicTime) {
            return VariableStochasticTimeCaster.getVariableStochasticTime((DeterministicTime) stochasticTime);
        }
        if (stochasticTime instanceof UniformTime) {
            return VariableStochasticTimeCaster.getVariableStochasticTime((UniformTime) stochasticTime);
        }
        if (stochasticTime instanceof ExponentialTime) {
            return VariableStochasticTimeCaster.getVariableStochasticTime((ExponentialTime) stochasticTime);
        }
        throw new RuntimeException("Class " + stochasticTime.getClass().getName() + " not implemented");
    }

    private static VariableStochasticTime getVariableStochasticTime(TruncatedExponentialTime stochasticTime) {
        return new VariableTruncatedExponentialTime(stochasticTime.getEFT().doubleValue(),
                stochasticTime.getLFT().doubleValue(), stochasticTime.getRate().doubleValue());
    }

    private static VariableStochasticTime getVariableStochasticTime(HypoExponentialTime stochasticTime) {
        return new VariableHypoExponentialTime(stochasticTime.getRate1().doubleValue(),
                stochasticTime.getRate2().doubleValue());
    }

    private static VariableStochasticTime getVariableStochasticTime(DeterministicTime stochasticTime) {
        return new VariableDeterministicTime(stochasticTime.getValue().doubleValue());
    }

    private static VariableStochasticTime getVariableStochasticTime(UniformTime stochasticTime) {
        return new VariableUniformTime(stochasticTime.getEFT().doubleValue(), stochasticTime.getLFT().doubleValue());
    }

    private static VariableStochasticTime getVariableStochasticTime(ExponentialTime stochasticTime) {
        return new VariableExponentialTime(stochasticTime.getRate().doubleValue());
    }
}
