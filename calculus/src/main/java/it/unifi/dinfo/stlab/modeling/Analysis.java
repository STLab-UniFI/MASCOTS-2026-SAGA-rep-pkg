package it.unifi.dinfo.stlab.modeling;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable data container holding the result of a CDF (Cumulative Distribution
 * Function) analysis for a single {@link SagaTask} component.
 *
 * <p>
 * Each instance stores the discrete CDF array, the time horizon limit, and the
 * time step used during analysis. The CDF array {@code values} is indexed such
 * that {@code values[i] = P(completion ≤ i * timeStep)}.
 *
 * @see SagaTask#analyzeAndSave(AnalysisType, double, double)
 * @see AnalysisType
 */
public class Analysis implements Cloneable {

    /** Discrete CDF values where {@code values[i] = P(completion ≤ i * timeStep)}. */
    private double[] values;

    /** Maximum time horizon used for the analysis (seconds). */
    private double timeLimit;

    /** Time step between consecutive CDF samples (seconds per array element). */
    private double timeStep;

    /**
     * Creates a new analysis result.
     *
     * @param values    the discrete CDF array
     * @param timeLimit maximum time horizon (seconds)
     * @param timeStep  time step between samples (seconds)
     */
    public Analysis(double[] values, double timeLimit, double timeStep) {
        this.values = values;
        this.timeLimit = timeLimit;
        this.timeStep = timeStep;
    }

    public double[] getValues() {
        return this.values;
    }

    public double getTimeLimit() {
        return this.timeLimit;
    }

    public double getTimeStep() {
        return this.timeStep;
    }

    @Override
    public Analysis clone() {
        try {
            Analysis cloned = (Analysis) super.clone();
            if (this.values != null) {
                cloned.values = this.values.clone();
            }
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public static Analysis importToCSV(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        List<Double> latencyList = new ArrayList<>();
        List<Double> cdfList = new ArrayList<>();

        // Lettura del file con BufferedReader (simmetrico al tuo BufferedWriter)
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine(); // Salta l'header (latency_ms,cdf)
            if (header == null) {
                throw new IOException("CSV file empty.");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] tokens = line.split(",");
                if (tokens.length >= 2) {
                    try {
                        latencyList.add(Double.parseDouble(tokens[0]));
                        cdfList.add(Double.parseDouble(tokens[1]));
                    } catch (NumberFormatException e) {
                        // Gestisce eventuali problemi di parsing radioattivi
                        throw new IOException("Format error in csv data.", e);
                    }
                }
            }
        }

        if (cdfList.isEmpty()) {
            throw new IOException("No valid data found.");
        }

        // Converti la lista dinamica nell'array primitivo double[] richiesto
        double[] values = new double[cdfList.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = cdfList.get(i);
        }

        // Ricava il timeLimit (l'ultimo valore di latenza)
        double timeLimit = latencyList.get(latencyList.size() - 1);

        // Ricava il timeStep (differenza tra il secondo e il primo elemento)
        double timeStep = latencyList.size() > 1 ? (latencyList.get(1) - latencyList.get(0)) : 0.0;

        // Ritorna la nuova istanza popolata
        return new Analysis(values, timeLimit, timeStep);
    }

    public void exportToCSV(String filePath) throws IOException {

        // Add .csv extension if missing
        if (!filePath.toLowerCase().endsWith(".csv")) {
            filePath = filePath + ".csv";
        }

        // Create directories if needed
        Path path = Paths.get(filePath);
        Path parent = path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Header CSV
            writer.write("latency_ms,cdf");
            writer.newLine();

            for (int i = 0; i < this.values.length; i++) {
                double latency = i * this.timeStep;
                double cdfValue = this.values[i];

                writer.write(latency + "," + cdfValue);
                writer.newLine();
            }
        }
    }

    /**
     * Creates a new Analysis object interpolated to a new time step and time limit.
     *
     * @param newTimeStep  The target smaller time step (seconds).
     * @param newTimeLimit The target larger time limit (seconds).
     * @return A new Analysis instance with the interpolated CDF values.
     */
    public Analysis resample(double newTimeStep, double newTimeLimit) {
        // 1. Calculate the size of the new CDF array
        // Adding 1 because the index 0 represents time = 0
        int newLength = (int) Math.round(newTimeLimit / newTimeStep) + 1;
        double[] newValues = new double[newLength];

        double[] oldValues = this.getValues(); // Assuming getter exists
        double oldTimeStep = this.getTimeStep();
        double oldTimeLimit = this.getTimeLimit();

        // 2. Interpolate values for the new time grid
        for (int j = 0; j < newLength; j++) {
            double currentTime = j * newTimeStep;

            if (currentTime >= oldTimeLimit) {
                // Extrapolation: If the new time exceeds the old limit, 
                // the CDF holds its maximum achieved probability.
                newValues[j] = oldValues[oldValues.length - 1];
            } else {
                // Linear Interpolation
                double oldIndexExact = currentTime / oldTimeStep;
                int lowIndex = (int) Math.floor(oldIndexExact);
                int highIndex = lowIndex + 1;

                if (highIndex < oldValues.length) {
                    double fraction = oldIndexExact - lowIndex;
                    // Formula: y = y0 + fraction * (y1 - y0)
                    newValues[j] = oldValues[lowIndex] + fraction * (oldValues[highIndex] - oldValues[lowIndex]);
                } else {
                    // Fallback for floating-point precision edge cases near the boundary
                    newValues[j] = oldValues[oldValues.length - 1];
                }
            }
        }

        // 3. Return the new immutable Analysis object
        // (Replace with your actual constructor or builder)
        return new Analysis(newValues, newTimeLimit, newTimeStep);
    }
}
