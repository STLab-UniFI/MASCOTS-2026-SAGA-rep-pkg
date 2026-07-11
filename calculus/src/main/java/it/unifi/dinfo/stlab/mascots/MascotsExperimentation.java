package it.unifi.dinfo.stlab.mascots;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.oristool.petrinet.PetriNet;
import org.oristool.util.xpnCreation.XpnGenerator;

import it.unifi.dinfo.stlab.generation.topologyLoading.TopologyLoader;
import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.utils.ScenarioInfo;

public class MascotsExperimentation {
    TopologyLoader topologyLoader;

    private MascotsExperimentation(double probForkJoin, double minProbabilityToFail, double maxProbabilityToFail, long seed) {
        this.topologyLoader = new TopologyLoader(probForkJoin, minProbabilityToFail, maxProbabilityToFail, seed);
    }

    public static MascotsExperimentation getDefault() {
        return new MascotsExperimentation(0.5, 0.1, 0.2, 42);
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
        String jsonFolder = "../alibaba_workflows/";
        String outputFolder = "../analysis-results/";

        // Clean the outputFolder
        try {
            cleanDirectory(Paths.get(outputFolder));
            System.out.println("Successfully cleaned output directory: " + outputFolder);
        } catch (IOException e) {
            System.err.println("Error cleaning output directory: " + e.getMessage());
            // Depending on your pipeline, you might want to stop execution here
            return;
        }

        MascotsExperimentation experimentation = MascotsExperimentation.getDefault();
        
        File folder = new File(jsonFolder);
        
        // Filter and list only JSON files
        File[] jsonFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (jsonFiles != null) {
            Arrays.sort(jsonFiles, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        }

        
        if (jsonFiles != null && jsonFiles.length > 0) {
            for (File file : jsonFiles) {
                String fileName = file.getName();
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
                
                try {

                    // 1. Load topology
                    SagaTask root = experimentation.loadTopology(file.getAbsolutePath());
    
                    // 2. Generate stpn and reward if needed
                    //experimentation.saveStpnAndReward(root, targetSubFolder.toString());
                    
                    // 3. Generate Yaml files
                    experimentation.generateYamlFiles(root, targetYamlSubFolder.getAbsolutePath());
                    
                    // 4. Generate Csv files
                    List<ScenarioInfo> allScenarios = experimentation.generateCsvFiles(root, targetCsvSubFolder.getAbsolutePath());
                    
                    // 5. Save probabilities
                    experimentation.saveProbabilities(allScenarios, targetSubFolder.toString());
    
                    // 6. Save number of iterations
                    //experimentation.saveNumberOfIterationsNeeded(allScenarios, targetSubFolder.toString());
                        
                    System.out.println("Successfully processed: " + fileName);
                    System.out.println("----------------------------------------");
                }
                catch (Exception e) {
                    System.out.println("Error processing topology: " + fileName);
                }
            }
        } else {
            System.out.println("No JSON files found in the specified source directory: " + jsonFolder);
        }
    }
}
