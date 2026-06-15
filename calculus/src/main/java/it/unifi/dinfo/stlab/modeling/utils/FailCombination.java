package it.unifi.dinfo.stlab.modeling.utils;

import java.util.ArrayList;
import java.util.List;

import org.oristool.petrinet.PetriNet;

import it.unifi.dinfo.stlab.modeling.SimpleTask;


/**
 * This class is used as an utilitary class to save informations about a scenario of failing.
 */
public class FailCombination {
    /**
     * Service that fails
     */
    private SimpleTask failedService;
    /**
     * Services in execution when the service was failing
     */
    private List<SimpleTask> servicesInExecution;

    /**
     * A petri net of the entire workflow. Is not correct to put this variable here
     * but we need it to construct the correct reward.
     */
    private PetriNet petriNet;

    public FailCombination(SimpleTask failedService, List<SimpleTask> servicesInExecution, PetriNet petriNet) {
        this.failedService = failedService;
        this.servicesInExecution = servicesInExecution;
        this.petriNet = petriNet;
    }

    public FailCombination(SimpleTask failedService, SimpleTask serviceInExecution, PetriNet petriNet) {
        this.failedService = failedService;
        this.servicesInExecution = new ArrayList<>();
        this.servicesInExecution.add(serviceInExecution);
        this.petriNet = petriNet;
    }

    public FailCombination(SimpleTask failedService) {
        this.failedService = failedService;
    }

    public static FailCombination happyPath() {
        return new FailCombination(null);
    }

    public static List<FailCombination> fromServiceGroup(List<SimpleTask> serviceGroup, PetriNet petriNet) {
        if (serviceGroup == null) throw new RuntimeException("Null argument -> serviceGroup is null");
        if (serviceGroup.isEmpty()) throw new RuntimeException("Empty list as input arguments -> serviceGroup is empty");

        List<FailCombination> returnList = new ArrayList<>();

        if (serviceGroup.size() == 1) returnList.add(new FailCombination(serviceGroup.get(0)));
        else {
            for (SimpleTask currentTask : serviceGroup) {
                List<SimpleTask> allServicesWithoutActualCurrentTask = new ArrayList<>(serviceGroup);
                allServicesWithoutActualCurrentTask.remove(currentTask);
                returnList.add(new FailCombination(currentTask, allServicesWithoutActualCurrentTask, petriNet));
            }
        }

        return returnList;
    }

    public SimpleTask getFailedService() {
        return this.failedService;
    }

    public void setFailedService(SimpleTask failedService) {
        this.failedService = failedService;
    }

    public List<FailCombination> getComplementaryCombinations() {
        if (this.servicesInExecution == null) {
            throw new RuntimeException("Before calling this function call isAndCombination()");
        }
        if (this.servicesInExecution.isEmpty()) {
            throw new RuntimeException("Before calling this function call isAndCombination()");
        }

        List<FailCombination> complementaryFailCombinations = new ArrayList<>();

        List<SimpleTask> allServices = new ArrayList<>();

        allServices.addAll(this.servicesInExecution);
        allServices.add(this.failedService);

        for (SimpleTask task : this.servicesInExecution) {
            List<SimpleTask> complementaryServicesInExecution = new ArrayList<>();
            complementaryServicesInExecution.addAll(allServices);
            complementaryServicesInExecution.remove(task);

            complementaryFailCombinations.add(new FailCombination(task, complementaryServicesInExecution, this.petriNet));
        }

        return complementaryFailCombinations;
    }

    public boolean isAndCombination() {
        return this.servicesInExecution != null && !this.servicesInExecution.isEmpty();
    }

    public boolean isHappyPath() {
        return this.failedService == null;
    }

    public List<SimpleTask> getServicesInExecution() {
        return servicesInExecution;
    }

    public void setServicesInExecution(List<SimpleTask> servicesInExecution) {
        this.servicesInExecution = servicesInExecution;
    }

    @Override
    public String toString() {
        if (this.isHappyPath())
            return "Happy Path";
        

        StringBuffer buffer = new StringBuffer();

        buffer.append("FAIL: " + this.failedService.getName() + "\n");
        if (this.isAndCombination()) {
            buffer.append("IN EXECUTION WHEN FAILED:");
            for (SimpleTask failedService : this.servicesInExecution) {
                buffer.append(" " + failedService.getName());
            }
            buffer.append("\n");
            buffer.append("REWARD: " + this.getRewardExpression());
        }

        return buffer.toString();
    }

    public String getRewardExpression() {
        if (this.isHappyPath())
            return "END";
        if (this.servicesInExecution == null) 
            return this.failedService.getFailedRewardString();
        if (this.servicesInExecution.isEmpty())
            return this.failedService.getFailedRewardString();
        
        String expression = "(" + this.failedService.getFailedRewardString() + ")";

        for (SimpleTask task : this.servicesInExecution) {
            expression += "&&(" + task.getInExecutionRewardString(this.petriNet) + ")";
        }

        return expression;
    }

    @Override
    public int hashCode() {
        if (this.failedService == null) 
            return this.toString().hashCode();
        return this.failedService.hashCode() + (this.servicesInExecution != null ? this.servicesInExecution.hashCode() : 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        FailCombination other = (FailCombination) obj;

        if (this.failedService == null && other.failedService != null) return false;
        if (this.failedService != null && !this.failedService.equals(other.failedService)) return false;

        if (this.servicesInExecution == null && other.servicesInExecution != null) return false;
        if (this.servicesInExecution != null && !this.servicesInExecution.equals(other.servicesInExecution)) return false;

        return true;
    }
}
