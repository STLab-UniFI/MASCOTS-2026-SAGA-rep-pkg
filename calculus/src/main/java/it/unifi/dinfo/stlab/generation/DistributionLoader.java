package it.unifi.dinfo.stlab.generation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.unifi.dinfo.stlab.generation.distributionTemplates.CpuDistributionTemplate;
import it.unifi.dinfo.stlab.generation.distributionTemplates.IoDistributionTemplate;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableHypoExponentialTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableTruncatedExponentialTime;

public class DistributionLoader {

    Map<String, VariableTruncatedExponentialTime> cpuDistributionsMap;
    Map<Integer, VariableHypoExponentialTime> ioDistributionsMap;

    ArrayList<VariableTruncatedExponentialTime> cpuDistributions;
    ArrayList<VariableHypoExponentialTime> ioDistributions;

    Random randomGenerator;

    public DistributionLoader(InputStream cpuDistributionStream, InputStream ioDistributionStream, long seed) {
        this.cpuDistributionsMap = new HashMap<>();
        this.ioDistributionsMap = new HashMap<>();

        this.cpuDistributions = new ArrayList<>();
        this.ioDistributions = new ArrayList<>();

        this.loadCPUDistribution(cpuDistributionStream);
        this.loadIODistribution(ioDistributionStream);

        this.randomGenerator = new Random(seed);
    }

    public static DistributionLoader getDefaultDistributionLoader() {
        InputStream cpuDistributionStream = DistributionLoader.class.getResourceAsStream("/cpu_distributions.json");
        InputStream iodistributionStream = DistributionLoader.class.getResourceAsStream("/io_distributions.json");

        return new DistributionLoader(cpuDistributionStream, iodistributionStream, 42);
    }

    public void loadCPUDistribution(InputStream distributionStream) {
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            List<CpuDistributionTemplate> templates = mapper.readValue(
                        distributionStream,
                        new TypeReference<List<CpuDistributionTemplate>>() {}
                );
            for (CpuDistributionTemplate distributionTemplate : templates) {
                this.cpuDistributionsMap.put(distributionTemplate.getName(), new VariableTruncatedExponentialTime(distributionTemplate.getA(), distributionTemplate.getB(), distributionTemplate.getLambda()));
                this.cpuDistributions.add(new VariableTruncatedExponentialTime(distributionTemplate.getA(), distributionTemplate.getB(), distributionTemplate.getLambda()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public void loadIODistribution(InputStream distributionStream) {
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            Map<String, IoDistributionTemplate> templates = mapper.readValue(
                        distributionStream,
                        new TypeReference<Map<String, IoDistributionTemplate>>() {}
                );
            for (IoDistributionTemplate distributionTemplate : templates.values()) {
                this.ioDistributionsMap.put(distributionTemplate.getnLines(), new VariableHypoExponentialTime(distributionTemplate.getLambda1(), distributionTemplate.getLambda2()));
                this.ioDistributions.add(new VariableHypoExponentialTime(distributionTemplate.getLambda1(), distributionTemplate.getLambda2()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public VariableHypoExponentialTime getRandomIoDistribution() {
        VariableHypoExponentialTime sampledVariable = this.ioDistributions.get(this.randomGenerator.nextInt(this.ioDistributions.size()));
        return new VariableHypoExponentialTime(sampledVariable.getRate1().doubleValue(), sampledVariable.getRate2().doubleValue());
    }

    public VariableTruncatedExponentialTime getCpuDistribution(int index, double scaleFactor) {
        VariableTruncatedExponentialTime sampledVariable = this.cpuDistributions.get(index);
        return new VariableTruncatedExponentialTime(sampledVariable.getEFT().doubleValue()*scaleFactor, sampledVariable.getLFT().doubleValue()*scaleFactor, sampledVariable.getRate().doubleValue()/scaleFactor);
    }

    public VariableTruncatedExponentialTime getRandomCpuDistribution() {
        VariableTruncatedExponentialTime sampledVariable = this.cpuDistributions.get(this.randomGenerator.nextInt(this.cpuDistributions.size()));
        return new VariableTruncatedExponentialTime(sampledVariable.getEFT().doubleValue(), sampledVariable.getLFT().doubleValue(), sampledVariable.getRate().doubleValue());
    }

    public VariableTruncatedExponentialTime getRandomCpuDistribution(double scaleFactor) {
        VariableTruncatedExponentialTime sampledVariable = this.cpuDistributions.get(this.randomGenerator.nextInt(this.cpuDistributions.size()));
        return new VariableTruncatedExponentialTime(sampledVariable.getEFT().doubleValue()*scaleFactor, sampledVariable.getLFT().doubleValue()*scaleFactor, sampledVariable.getRate().doubleValue()/scaleFactor);
    }

    public int getNameOfIoDistribution(VariableHypoExponentialTime variable) {
        int matchedLines = 0;
        for (int nLines : this.ioDistributionsMap.keySet()) {
            if (variable.getRate1().equals(this.ioDistributionsMap.get(nLines).getRate1()) &&
                variable.getRate2().equals(this.ioDistributionsMap.get(nLines).getRate2())) {
                    matchedLines = nLines;
                }
        }
        
        if (matchedLines == 0) {
            throw new RuntimeException("No database matched for variable HypoExp with rate1=" + String.valueOf(variable.getRate1()) + " and rate2=" + String.valueOf(variable.getRate2()));
        }

        return matchedLines;
    }

    public VariableHypoExponentialTime getIoDistribution(int nLines) {
        return this.ioDistributionsMap.get(nLines);
    }

    public VariableHypoExponentialTime getIoDistributionFromMean(double mean, double scaleFactor, double epsilon) {
        VariableHypoExponentialTime returnVariable = null;

        for (VariableHypoExponentialTime ioVariable : this.ioDistributions) {
            if (Math.abs(ioVariable.clone().getExpectedValue() - mean/scaleFactor) < epsilon) {
                if (returnVariable != null) {
                    throw new RuntimeException("More than 1 variable matched!");
                }
                returnVariable = new VariableHypoExponentialTime(ioVariable.getRate1().doubleValue()/scaleFactor, ioVariable.getRate2().doubleValue()/scaleFactor);
            }
        }

        return returnVariable;
    }

    public VariableTruncatedExponentialTime getCpuDistributionFromMean(double mean, double scaleFactor, double epsilon) {
        VariableTruncatedExponentialTime returnVariable = null;

        for (VariableTruncatedExponentialTime cpuVariable : this.cpuDistributions) {
            if (Math.abs(cpuVariable.clone().getExpectedValue() - mean/scaleFactor) < epsilon) {
                if (returnVariable != null) {
                    throw new RuntimeException("More than 1 variable matched!");
                }
                returnVariable = new VariableTruncatedExponentialTime(cpuVariable.getEFT().doubleValue()*scaleFactor, cpuVariable.getLFT().doubleValue()*scaleFactor, cpuVariable.getRate().doubleValue()/scaleFactor);
            }
        }

        if (returnVariable == null) {
            throw new RuntimeException("No variable matched!");
        }

        return returnVariable;
    }
}
