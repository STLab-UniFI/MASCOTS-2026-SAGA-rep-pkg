package it.unifi.dinfo.stlab.modeling.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.SimpleTask;

public class ScenarioInfo {
    private final String name;
    private Analysis analysis;
    private final double probability;
    private final FailCombination failCombination;

    private ScenarioInfo(String name, Analysis analysis, double probability, FailCombination failCombination) {
        this.name = name;
        this.analysis = analysis;
        this.probability = probability;
        this.failCombination = failCombination;
    }

    public static ScenarioInfo timeToConsistency(Analysis analysis) {
        return new ScenarioInfo(
            "Time To Consistency",
            analysis,
            1.0,
            null
        );
    }

    public static ScenarioInfo happyPath(Analysis analysis, double probability) {
        return new ScenarioInfo(
            "Happy Path", 
            analysis, 
            probability, 
            null
        );
    }

    public static ScenarioInfo failingScenario(Analysis analysis, double probability, FailCombination failCombination) {
        if (failCombination == null) {
            return ScenarioInfo.happyPath(analysis, probability);
        }

        if (failCombination.isHappyPath()) {
            return ScenarioInfo.happyPath(analysis, probability);
        }

        return new ScenarioInfo(
            failCombination.getFailedService().getKubernetesSanitazedName(), 
            analysis, 
            probability, 
            failCombination
        );
    }

    public static ScenarioInfo aggregateScenario(Analysis analysis, double probability, SimpleTask aggregationTask) {
        return new ScenarioInfo(
            aggregationTask.getKubernetesSanitazedName(),
            analysis,
            probability,
            new FailCombination(aggregationTask)
        );
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public double getProbability() {
        return probability;
    }

    public FailCombination getFailCombination() {
        return failCombination;
    }

    public String getName() {
        return name;
    }

    public boolean isAggregable() {
        return this.failCombination != null;
    }

    public SimpleTask getFailingService() {
        if (this.failCombination == null)
            throw new RuntimeException("Before calling this method you need to check if the scenario is a scenario where a service is failed");
        return this.failCombination.getFailedService();
    }

    public boolean isHappyPath() {
        return this.failCombination == null;
    }

    public boolean isTimeToConsistency() {
        return this.name == "Time To Consistency";
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("####################\n");
        buffer.append(this.name + "\n");
        buffer.append("Probability: " + this.probability +"\n");

        return buffer.toString();
    }

    public String getNameForFile() {
        return this.name
            .trim()
            .toLowerCase()
            .replaceAll("\\s+", "-")         
            .replaceAll("[^a-z0-9\\-_]", "");
    }

    public static void writeCsvs(List<ScenarioInfo> scenarios, String outputPath) throws IOException {
        prepareOutputDirectory(Path.of(outputPath));

        for (ScenarioInfo scenario : scenarios) {
            Path joinPath = Path.of(outputPath, scenario.getNameForFile());
            scenario.getAnalysis().exportToCSV(joinPath.toString());
        }
    }

    /**
     * Ensures the target directory exists and is completely empty.
     */
    private static void prepareOutputDirectory(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        } else {
            // Clean the directory contents if it already exists
            try (var stream = Files.walk(outputDir)) {
                stream.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(outputDir)) // Do not delete the root directory itself
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + path, e);
                        }
                    });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw e;
            }
        }
    }

    public static double getMinProbability(List<ScenarioInfo> allScenarios) {
        double pMin = Double.MAX_VALUE;

        for (ScenarioInfo scenario : allScenarios) {
            if (scenario.getProbability() < pMin)
                pMin = scenario.getProbability();
        }

        return pMin;
    }

    public String getNameAndProbability() {
        return this.name + ", " + this.probability;
    }

    public static ScenarioInfo getTimeToConsistencyScenario(List<ScenarioInfo> scenarios) {
        for (ScenarioInfo scenario : scenarios){
            if (scenario.isTimeToConsistency())
                return scenario;
        }
        return null;
    }

    public ScenarioInfo resample(double timeLimit, double timeStep) {
        this.analysis = this.analysis.resample(timeStep, timeLimit);
        return this;
    }
}
