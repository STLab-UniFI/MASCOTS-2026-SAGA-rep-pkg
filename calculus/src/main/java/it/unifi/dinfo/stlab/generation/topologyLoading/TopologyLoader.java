package it.unifi.dinfo.stlab.generation.topologyLoading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableDeterministicTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableErlangTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableExponentialTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableGeneralizedErlangTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableHyperExponentialTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableHypoExponentialTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableStochasticTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableTruncatedExponentialTime;

public class TopologyLoader {

    private double probForkJoin;
    private double minProbabilityToFail;
    private double maxProbabilityToFail;

    private Map<TopologyTaskDto, SimpleTask> associationMap;

    // Used for the services names
    private int serviceCounter;
    Random randomGenerator;

    DistributionDtoToDistributionMapper distributionMapper;

    private boolean andAlreadyUsedInTheTopology;

    public TopologyLoader(double probForkJoin, double minProbabilityToFail, double maxProbabilityToFail, long seed) {
        this.probForkJoin = probForkJoin;

        if (minProbabilityToFail > maxProbabilityToFail)
            throw new RuntimeException("Min probability is greater than max probability -> " + minProbabilityToFail + " > " + maxProbabilityToFail);

        this.minProbabilityToFail = minProbabilityToFail;
        this.maxProbabilityToFail = maxProbabilityToFail;

        this.associationMap = new HashMap<>();
        this.serviceCounter = 0;
        this.randomGenerator = new Random(seed);

        this.distributionMapper = new DistributionDtoToDistributionMapper();

        this.andAlreadyUsedInTheTopology = false;
    }

    public SagaTask loadFromFile(String filePathString) {
        this.serviceCounter = 0;

        String content = this.getFileContent(filePathString);

        TopologyTaskDto sagaTaskDto = this.mapContentToTopologyTaskDto(content);

        SagaTask root = this.mapTopologyTaskDtoToSagaTask(sagaTaskDto);

        return root;
    }

    private String getFileContent(String filePathString) {
        Path filePath = Paths.get(filePathString);
        String content;

        if (Files.exists(filePath)) {
            try {
                content = Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Error reading the file: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("The file does not exist at the specified path.");
        }

        return content;
    }

    private TopologyTaskDto mapContentToTopologyTaskDto(String content) {
        ObjectMapper mapper = new ObjectMapper();
        TopologyTaskDto sagaTaskDto;
    
        try {
            sagaTaskDto = mapper.readValue(content, TopologyTaskDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Some errors occurred in mapping the JSON to the Java object.");
        }

        return sagaTaskDto;
    }

    private SagaTask mapTopologyTaskDtoToSagaTask(TopologyTaskDto rootTaskDto) {
        this.constructAssociationMap(rootTaskDto);
        
        // Edge case: single service
        if (rootTaskDto.getChildrenSize() == 0) {
            return SagaTask.seq("root-seq", this.associationMap.get(rootTaskDto));
        }
        // Normal case: I call the method to construct the topology
        return this.recursiveSagaTaskCreation(rootTaskDto);
    }

    private SagaTask recursiveSagaTaskCreation(TopologyTaskDto taskDto) {
        if (taskDto.getChildrenSize() == 0) {
            return this.associationMap.get(taskDto);
        }
        else if (taskDto.getChildrenSize() == 1) {
            return SagaTask.seq(
                "seq-" + this.associationMap.get(taskDto).getName() + "-" + this.generateRandomString(3), 
                this.associationMap.get(taskDto), 
                this.recursiveSagaTaskCreation(taskDto.getChildren().get(0))
            );
        }
        else {
            return SagaTask.seq(
                "seq-" + this.associationMap.get(taskDto).getName() + "-" + this.generateRandomString(3), 
                this.associationMap.get(taskDto),
                this.recursiveSagaTaskCreationComposite(taskDto.getChildren())
            );
        }
    }

    private SagaTask recursiveSagaTaskCreationComposite(List<TopologyTaskDto> taskDtos) {
        if (taskDtos.size() <= 1) {
            throw new RuntimeException("Size of taskDtos is less than 2");
        }

        List<SagaTask> children = new ArrayList<>();

        for (TopologyTaskDto taskDto : taskDtos) {
            children.add(this.recursiveSagaTaskCreation(taskDto));
        }

        double sampledProbabilityToHaveForkJoin = this.randomGenerator.nextDouble();
        
        if (sampledProbabilityToHaveForkJoin <= this.probForkJoin && !this.andAlreadyUsedInTheTopology) {
            this.andAlreadyUsedInTheTopology = true;
            return SagaTask.and("and-" + this.associationMap.get(taskDtos.get(0)).getName() + "-" + this.generateRandomString(3), children.toArray(new SagaTask[0]));
        }
        else {
            return SagaTask.xor("xor-" + this.associationMap.get(taskDtos.get(0)).getName() + "-" + this.generateRandomString(3), children, this.getArrayOfProbabilitiesForXor(taskDtos.size()));
        }
    }

    private List<Double> getArrayOfProbabilitiesForXor(int size) {
        List<Double> probabilities = new ArrayList<>();

        double cumulativeProbability = 0.;

        for (int i = 0; i < size; i++) {
            // Se non sono all'ultimo elemento posso assegnare una probabilità randomica
            if (i != size-1) {
                double prob = this.randomGenerator.nextDouble() * (1-cumulativeProbability);
                cumulativeProbability += prob;

                probabilities.add(prob);
            }
            // Se invece sono ad assegnare la probabilità all'ultimo elemento devo assegnare il complementare della probabilità cumulata
            else {
                probabilities.add(1.-cumulativeProbability);
            } 
        }

        return probabilities;
    }

    private void constructAssociationMap(TopologyTaskDto root) {
        this.associationMap = new HashMap<>();
        
        List<TopologyTaskDto> taskList = root.getTasksAsList();

        for (TopologyTaskDto taskDto : taskList) {
            associationMap.put(taskDto, this.constructSimpleTask(taskDto));
        }
    }

    private SimpleTask constructSimpleTask(TopologyTaskDto taskDto) {
        if (taskDto.getDistributionSize() != 3)
            throw new RuntimeException("The distributions should be 3!");

        // Construct the distribution using the mapper
        VariableStochasticTime forwardDistribution = this.distributionMapper.constructDistribution(taskDto.getExecution_time_distribution().get(0));
        VariableStochasticTime failureDistribution = this.distributionMapper.constructDistribution(taskDto.getExecution_time_distribution().get(1));
        VariableStochasticTime compensationDistribution = this.distributionMapper.constructDistribution(taskDto.getExecution_time_distribution().get(2));

        SimpleTask task = SagaTask.simple(
            this.generateServiceName(), 
            forwardDistribution, 
            compensationDistribution, 
            failureDistribution, 
            this.getRandomProbabilityToFail()
        );

        return task;
    }

    private String generateRandomString(int length) {
        if (length < 1) return "";

        String letters = "abcdefghijklmnopqrstuvwxyz";
        String allChars = letters + "0123456789";
        
        StringBuilder result = new StringBuilder(length);

        // 1. Force the first character to be a letter
        result.append(letters.charAt(this.randomGenerator.nextInt(letters.length())));

        // 2. Fill the rest of the string with alphanumeric characters
        for (int i = 1; i < length; i++) {
            result.append(allChars.charAt(this.randomGenerator.nextInt(allChars.length())));
        }

        return result.toString();
    }

    private String generateServiceName() {
        this.serviceCounter++;

        return "s" + String.format("%03d", this.serviceCounter);
    }

    private double getRandomProbabilityToFail() {
        return this.randomGenerator.nextDouble() * (this.maxProbabilityToFail - this.minProbabilityToFail) + this.minProbabilityToFail;
    }

    public static void main(String[] args) {
        String path = "/home/tommaso/SAGA/SAGAConsistencyOpt/call_graph_enriched/call_graph_5_nodes_1.json";
        long seed = 42;

        TopologyLoader loader = new TopologyLoader(0.5, 0.1, 0.5, seed);
        SagaTask root = loader.loadFromFile(path);

        System.out.println(root.workflowString());
    }
}
