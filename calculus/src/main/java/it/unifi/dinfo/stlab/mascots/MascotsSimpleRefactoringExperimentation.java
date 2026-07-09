package it.unifi.dinfo.stlab.mascots;

import it.unifi.dinfo.stlab.generation.topologyLoading.TopologyLoader;
import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.rearrangement.*;
import it.unifi.dinfo.stlab.modeling.utils.ScenarioInfo;
import org.oristool.petrinet.PetriNet;
import org.oristool.util.xpnCreation.XpnGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MascotsSimpleRefactoringExperimentation {
    TopologyLoader topologyLoader;

    // todo complete the strategy list
    private static final List<EvaluatorStrategy> STRATEGIES = List.of(new TimeToConsistencyEvaluator(), new FailFastEvaluator(), new CompensateFastEvaluator(), new ConvergeFastEvaluator());


    private MascotsSimpleRefactoringExperimentation(double probForkJoin, double minProbabilityToFail, double maxProbabilityToFail, long seed) {
        this.topologyLoader = new TopologyLoader(probForkJoin, minProbabilityToFail, maxProbabilityToFail, seed);
    }

    public static MascotsSimpleRefactoringExperimentation getDefault() {
        return new MascotsSimpleRefactoringExperimentation(0.5, 0.1, 0.2, 42);
    }

    private SagaTask loadTopology(String jsonPath) {
        System.out.println(">>>>>>> Load topology");
        return this.topologyLoader.loadFromFile(jsonPath);
    }

    private void generateYamlFiles(SagaTask root, String outputDir) throws Exception {
        System.out.println(">>>>>>> Generate YAML files");
        MascotsYamlGenerator.generateYamlFiles(root, outputDir);
    }

    private List<ScenarioInfo> generateCsvFiles(SagaTask root, String outputDir) throws Exception {
        System.out.println(">>>>>>> Generate csv files");
        List<ScenarioInfo> allScenarios = root.calculateAllScenarioAnalysis();
        ScenarioInfo.writeCsvs(allScenarios, outputDir);
        return allScenarios;
    }

    private void saveStpnAndReward(SagaTask root, String outputDir) {
        System.out.println(">>>>>>> Save STPN and reward");
        PetriNet petriNet = root.getStpnModel();
        XpnGenerator generator = new XpnGenerator(petriNet);
        generator.saveToFile(outputDir + "/petri-net.xpn");

        String rewards = root.getAllRewardCombinations();
    
        // Save the rewards string into a file
        try {
            Path rewardFilePath = Paths.get(outputDir, "rewards.txt");
            Files.writeString(rewardFilePath, rewards);
        } catch (IOException e) {
            // Log the error or throw a RuntimeException depending on your test harness setup
            System.err.println("Error saving reward combinations to file: " + e.getMessage());
            throw new RuntimeException("Could not save rewards configuration", e);
        }
    }

    /**
     * Deletes all files and subdirectories inside the given path.
     * If the path doesn't exist, it creates it.
     */
    private static void cleanDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            // Files.walk visits subdirectories. Sorting in reverse order ensures 
            // files and inner directories are deleted before their parent folders.
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete " + path, e);
                        }
                    });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw e;
            }
        }
        
        // Recreate the root empty directory
        Files.createDirectories(dir);
    }

    private void saveProbabilities(List<ScenarioInfo> allScenarios, String outputDir) {
        Path savingPath = Path.of(outputDir, "probabilities.txt");
    
        String outputContent = allScenarios.stream()
                .map(ScenarioInfo::getNameAndProbability)
                .collect(Collectors.joining("\n"));

        try {
            Files.writeString(savingPath, outputContent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save probabilities", e);
        }
    }

    private void saveNumberOfIterationsNeeded(List<ScenarioInfo> allScenarios, String outputDir) {
        Path savingPath = Path.of(outputDir, "iterations.txt");

        double pMin = ScenarioInfo.getMinProbability(allScenarios);
        int numberIterations = (int)(Math.pow(1.96/0.05, 2) * (1.-pMin) / pMin);

        try {
            Files.writeString(savingPath, String.valueOf(numberIterations));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save probabilities", e);
        }
    }

    public static void main(String[] args) throws Exception {
        String jsonFolder = "./jsonTopologies";
        String baseFolderAnalysis = "./analysis-results";
        String outputFolder = "./refactoring-results";

        // Clean the outputFolder
        try {
            cleanDirectory(Paths.get(outputFolder));
            System.out.println("Successfully cleaned output directory: " + outputFolder);
        } catch (IOException e) {
            System.err.println("Error cleaning output directory: " + e.getMessage());
            // Depending on your pipeline, you might want to stop execution here
            return;
        }

        MascotsSimpleRefactoringExperimentation experimentation = MascotsSimpleRefactoringExperimentation.getDefault();
        
        File folder = new File(jsonFolder);
        
        // Filter and list only JSON files
        File[] jsonFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (jsonFiles != null) {
            Arrays.sort(jsonFiles, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        }

        if (jsonFiles != null && jsonFiles.length > 0) {
            for (File file : jsonFiles) {
                String fileName = file.getName();
                String graphName = fileName.replace(".json", "");
                // Extract filename without extension
                String folderName = fileName.substring(0, fileName.lastIndexOf('.'));
                
                // Define the specific subfolder target
                File targetSubFolder = new File(outputFolder, folderName);
                File targetYamlSubFolder = new File(outputFolder, folderName+ "/yamls");
                File targetCsvSubFolder = new File(outputFolder, folderName+ "/csvs");
                
                // Ensure target directories exist
                if (!targetSubFolder.exists()) {
                    targetSubFolder.mkdirs();
                }
                if (!targetYamlSubFolder.exists()) {
                    targetYamlSubFolder.mkdirs();
                }
                if (!targetCsvSubFolder.exists()) {
                    targetCsvSubFolder.mkdirs();
                }

                System.out.println("Processing: " + fileName + " -> Target folder: " + targetSubFolder.getName());
                
                // 1. Load topology
                SagaTask root = experimentation.loadTopology(file.getAbsolutePath());

                DistributionRanker ranker = new DistributionRanker(new CDFComparisonDominanceComparator());
                Map<SagaTask, String> refactoringMap = new HashMap<>();

                Path analysisPath = Path.of(baseFolderAnalysis, graphName, "csvs", "time-to-consistency.csv");
                Analysis originalAnalysis = null;
                try {
                    originalAnalysis = Analysis.importToCSV(analysisPath.toAbsolutePath().toString());
                }
                catch (Exception e) {
                    System.out.println("SKIP WORKFLOW " + analysisPath.toAbsolutePath().toString() + " not found");
                }
                ranker.addTask(root, originalAnalysis.getValues(), originalAnalysis.getTimeStep());
                refactoringMap.put(root, "Original");

                SagaTask failLast = SimpleTopologyRefactor.sequentialFailLast(root.clone());
                refactoringMap.put(failLast, "fail-fast");
                Analysis failLastAnalysis = failLast.analyzeTimeToConsistency();
                failLastAnalysis.exportToCSV(targetCsvSubFolder.getAbsolutePath() + "/fail-last.csv");
                ranker.addTask(failLast, failLastAnalysis.getValues(), failLastAnalysis.getTimeStep());
                experimentation.generateYamlFiles(failLast, targetYamlSubFolder.getAbsolutePath() + "/fail-last");

                SagaTask sequential = SimpleTopologyRefactor.sequential(root.clone());
                refactoringMap.put(sequential, "sequential");
                Analysis seqAnalisys = sequential.analyzeTimeToConsistency();
                seqAnalisys.exportToCSV(targetCsvSubFolder.getAbsolutePath() + "/sequential.csv");
                ranker.addTask(sequential, seqAnalisys.getValues(), seqAnalisys.getTimeStep());
                experimentation.generateYamlFiles(sequential, targetYamlSubFolder.getAbsolutePath() + "/sequential");

                SagaTask failFirst = SimpleTopologyRefactor.sequentialFailFirst(root.clone());
                refactoringMap.put(failFirst, "fail-first");
                Analysis failFirstAnalisys = failFirst.analyzeTimeToConsistency();
                failFirstAnalisys.exportToCSV(targetCsvSubFolder.getAbsolutePath() + "/fail-first.csv");
                ranker.addTask(failFirst, failFirstAnalisys.getValues(), failFirstAnalisys.getTimeStep());
                experimentation.generateYamlFiles(failFirst, targetYamlSubFolder.getAbsolutePath() + "/fail-first");

                Map<SagaTask, Double> ranking = ranker.getRanking();
                saveRanking(refactoringMap, ranking, targetCsvSubFolder.getAbsolutePath());

                System.out.println("Successfully processed: " + fileName);
                System.out.println("----------------------------------------");
            }
        } else {
            System.out.println("No JSON files found in the specified source directory: " + jsonFolder);
        }
    }

    private static void saveRanking(Map<SagaTask, String> refactoringMap, Map<SagaTask, Double> rankingMap, String outputDir){
        Path savingPath = Path.of(outputDir, "ranking.txt");

        String outputContent = "";
        for(Map.Entry<SagaTask, String> entry : refactoringMap.entrySet()){
            outputContent += entry.getValue() + ", " + rankingMap.get(entry.getKey()) +"\n";
        }
        try {
            Files.writeString(savingPath, outputContent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save ranking", e);
        }
    }

}
