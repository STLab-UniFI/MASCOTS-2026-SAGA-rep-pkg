package it.unifi.dinfo.stlab.generation.topologyLoading;

import it.unifi.dinfo.stlab.modeling.Constants;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableDeterministicTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableExponentialTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableGeneralizedErlangTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableHyperExponentialTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableHypoExponentialTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableStochasticTime;

public class DistributionDtoToDistributionMapper {
    public VariableStochasticTime constructDistribution(DistributionDto distributionDto) {
        if (distributionDto.getType().equals("Deterministic"))
            return this.constructDeterministic(distributionDto);
        if (distributionDto.getType().equals("Hypo-exponential"))
            return this.constructHypoExponential(distributionDto);
        if (distributionDto.getType().equals("Hyper-exponential"))
            return this.constructHyperExponential(distributionDto);
        if (distributionDto.getType().equals("Generalized-Erlang")) {
            VariableGeneralizedErlangTime originalVariable = (VariableGeneralizedErlangTime)this.constructGeneralizedErlang(distributionDto);
            VariableStochasticTime finalVariable = originalVariable;
            if (originalVariable.getK() > Constants.K_PHASES_FOR_GEN_ERLANG_CONVERSION_TO_SHIFTED_EXP) {
                finalVariable = originalVariable.convertToShiftedExponential();
                System.out.println(">>>> Conversion");
                System.out.println(">>>> " + originalVariable);
                System.out.println(">>>> " + finalVariable);
            }
            return finalVariable;
        }
        if (distributionDto.getType().equals("Exponential"))
            return this.constructExponential(distributionDto);

        throw new RuntimeException("The type " + distributionDto.getType() + " is not implemented yet!");
    }

    private VariableStochasticTime constructDeterministic(DistributionDto distributionDto) {
        if (!distributionDto.getParams().keySet().contains("mean"))
            throw new RuntimeException("No mean value in the params map!");

        return new VariableDeterministicTime(distributionDto.getParams().get("mean"));
    }

    private VariableStochasticTime constructHypoExponential(DistributionDto distributionDto) {
        if (!distributionDto.getParams().keySet().contains("lambda1") || !distributionDto.getParams().keySet().contains("lambda2"))
            throw new RuntimeException("No lambda values in the params map!");

        return new VariableHypoExponentialTime(distributionDto.getParams().get("lambda1"), distributionDto.getParams().get("lambda2"));
    }

    private VariableStochasticTime constructHyperExponential(DistributionDto distributionDto) {
        if (!distributionDto.getParams().keySet().contains("p1") || !distributionDto.getParams().keySet().contains("p2"))
            throw new RuntimeException("No p values in the params map!");
        if (!distributionDto.getParams().keySet().contains("lambda1") || !distributionDto.getParams().keySet().contains("lambda2"))
            throw new RuntimeException("No lambda values in the params map!");
        
        return new VariableHyperExponentialTime(distributionDto.getParams().get("lambda1"), distributionDto.getParams().get("lambda2"), distributionDto.getParams().get("p1"));
    }

    private VariableStochasticTime constructGeneralizedErlang(DistributionDto distributionDto) {
        if (!distributionDto.getParams().keySet().contains("lambda1") || !distributionDto.getParams().keySet().contains("lambda2"))
            throw new RuntimeException("No lambda values in the params map!");
        if (!distributionDto.getParams().keySet().contains("k"))
            throw new RuntimeException("No k value in the params map!");
        
        return new VariableGeneralizedErlangTime(distributionDto.getParams().get("k").intValue(), distributionDto.getParams().get("lambda1"), distributionDto.getParams().get("lambda2"));
    }

    private VariableStochasticTime constructExponential(DistributionDto distributionDto) {
        if (!distributionDto.getParams().keySet().contains("rate"))
            throw new RuntimeException("No rate values in the params map!");

        return new VariableExponentialTime(distributionDto.getParams().get("rate"));
    }
}
