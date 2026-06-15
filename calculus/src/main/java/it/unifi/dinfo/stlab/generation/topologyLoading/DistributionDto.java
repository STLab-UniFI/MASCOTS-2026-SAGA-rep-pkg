package it.unifi.dinfo.stlab.generation.topologyLoading;

import java.util.Map;

public class DistributionDto {
    private String type;
    private Map<String, Double> params;

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public Map<String, Double> getParams() {
        return params;
    }
    public void setParams(Map<String, Double> params) {
        this.params = params;
    }
}
