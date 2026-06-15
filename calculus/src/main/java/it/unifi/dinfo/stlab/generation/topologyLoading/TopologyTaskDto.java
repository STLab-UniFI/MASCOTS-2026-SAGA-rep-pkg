package it.unifi.dinfo.stlab.generation.topologyLoading;

import java.util.ArrayList;
import java.util.List;

class TopologyTaskDto {
    private String id;
    private String type;
    private int tier;

    private List<DistributionDto> execution_time_distribution;

    private List<TopologyTaskDto> children;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public int getTier() {
        return tier;
    }
    public void setTier(int tier) {
        this.tier = tier;
    }
    public List<TopologyTaskDto> getChildren() {
        return children;
    }
    public void setChildren(List<TopologyTaskDto> children) {
        this.children = children;
    }
    public List<DistributionDto> getExecution_time_distribution() {
        return execution_time_distribution;
    }
    public void setExecution_time_distribution(List<DistributionDto> execution_time_distribution) {
        this.execution_time_distribution = execution_time_distribution;
    }
    
    public int getDistributionSize() {
        return this.execution_time_distribution.size();
    }

    public List<TopologyTaskDto> getTasksAsList() {
        List<TopologyTaskDto> simpleTasks = new ArrayList<>();
        
        if (this.children != null && !this.children.isEmpty()) {
            for (TopologyTaskDto child : this.children) {
                simpleTasks.addAll(child.getTasksAsList());
            }
        }

        simpleTasks.add(this);

        return simpleTasks;
    }

    public int getChildrenSize() {
        if (this.children == null) {
            return 0;
        }
        return this.children.size();
    }
}
