package it.unifi.dinfo.stlab.mascots;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import org.oristool.eulero.modeling.Simple;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.unifi.dinfo.stlab.modeling.CompositeTask;
import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableDeterministicTime;

public class MascotsYamlGenerator {
    /**
     * Function 1: Reads a file from the resources folder and returns its content as
     * a String.
     * * @param resourcePath The path to the file (e.g., "mascots/filename.yml")
     * 
     * @return The content of the file
     * @throws IOException If the file cannot be found or read
     */
    public static String readResourceFile(String resourcePath) throws IOException {
        // Load the file from the classpath
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);

        if (inputStream == null) {
            throw new IOException("File not found in resources: " + resourcePath);
        }

        // Read the InputStream into a String
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Function 2: Replaces a target string within the content with a new string.
     * * @param originalContent The original file content
     * 
     * @param target      The string to be replaced (e.g., "##CONFIGMAP##")
     * @param replacement The new string (e.g., your JSON string)
     * @return The updated content
     */
    public static String replacePlaceholder(String originalContent, String target, String replacement) {
        if (originalContent == null || target == null || replacement == null) {
            return originalContent;
        }

        return originalContent.replace(target, replacement);
    }

    public static void generateOrchestratorYaml(SagaTask root, String outputPath) throws IOException {
        String topologyString = root.toJsonNode(new ObjectMapper()).toPrettyString();
        String tabbedJsonConfigmap = topologyString.replaceAll("(?m)^", "    ");

        String filePath = "mascots/saga-orchestrator.yml";
        String yamlContent = readResourceFile(filePath);

        String finalYamlContent = replacePlaceholder(yamlContent, "##CONFIGMAP##", tabbedJsonConfigmap);

        // Generate a unique experiment ID based on the current timestamp
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
        LocalDateTime now = LocalDateTime.now();
        String experimentId = now.format(formatter);
        finalYamlContent = replacePlaceholder(finalYamlContent, "/EXPERIMENT_ID/", experimentId);

        Path outputFile = Paths.get(outputPath, "saga-orchestrator.yml");

        // 2. Ensure the parent directory exists before attempting to write
        // (Safeguard in case this is called independently of your directory creation
        // method)
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }

        // 3. Write the final YAML string to the file
        Files.writeString(outputFile, finalYamlContent);
        System.out.println("Created saga-orchestrator.yml: " + outputFile.toString());
    }

    public static void generateSimpleTaskYaml(SimpleTask task, String outputPath) throws IOException {
        String jsonConfigmap = task.getSimpleTasktoJson(new ObjectMapper()).toPrettyString();
        String tabbedJsonConfigmap = jsonConfigmap.replaceAll("(?m)^", "    ");

        String filePath = "mascots/service-template.yml";
        String yamlContent = readResourceFile(filePath);

        String finalYamlContent = replacePlaceholder(yamlContent, "##CONFIGMAP##", tabbedJsonConfigmap);
        finalYamlContent = replacePlaceholder(finalYamlContent, "SERVICE-NAME", task.getKubernetesSanitazedName());
        finalYamlContent = replacePlaceholder(finalYamlContent, "CONFIG_NAME", "config-" + task.getKubernetesSanitazedName());

        String filename = task.getName()
                .replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F\\x7F]", "_")
                .trim();
        Path outputFile = Paths.get(outputPath, filename + ".yml");

        // 2. Ensure the parent directory exists before attempting to write
        // (Safeguard in case this is called independently of your directory creation
        // method)
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }

        // 3. Write the final YAML string to the file
        Files.writeString(outputFile, finalYamlContent);
        System.out.println("Created " + filename + ".yml: " + outputFile.toString());
    }

    public static void generateSimpleTasksYaml(SagaTask root, String outputPath) throws IOException {
        for (SimpleTask task : root.getSimpleTasks()) {
            generateSimpleTaskYaml(task, outputPath);
        }
    }

    public static void generateYamlFiles(SagaTask root, String outputPath) {
        Path path = Paths.get(outputPath);

        try {
            // 1. If the path already exists, clean its contents and remove it
            if (Files.exists(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }

            // 2. Create the target path and all missing intermediate folders
            Files.createDirectories(path);

            // 3. Generate the orchestrator file
            generateOrchestratorYaml(root, outputPath);
            // 4. Generate the simple tasks file
            generateSimpleTasksYaml(root, outputPath);

            System.out.println("Output directory successfully prepared at: " + path.toAbsolutePath());

        } catch (IOException e) {
            // Wrapping in a RuntimeException so we don't change your original method
            // signature
            throw new RuntimeException("Failed to initialize and clean output directory: " + outputPath, e);
        }
    }
}
