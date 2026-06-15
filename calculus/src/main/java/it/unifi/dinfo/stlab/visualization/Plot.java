package it.unifi.dinfo.stlab.visualization;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import it.unifi.dinfo.stlab.modeling.Analysis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Class used to save comparison graph into a PNG image.
 * The image shows in a single CDF graph multiple lines.
 */
public class Plot {
    /**
     * Save CDF comparison image
     * @param cdfs array of array of double values ({@code cdfs[0]} represents a CDF)
     * @param names names to be put in legend. The order must be the same of {@code cdfs}
     * @param timeStep time step used in the cdfs (in this method all the cdfs have same time step)
     * @param fileName name of the file where the image is saved
     */
    public static void saveCdfComparison(double[][] cdfs, String[] names, double timeStep, String fileName) {
        // Safety check
        if (cdfs.length != names.length) {
            System.err.println("Error: Number of CDF arrays must match number of names.");
            return;
        }

        try {
            // 1. Create Dataset
            XYSeriesCollection dataset = new XYSeriesCollection();
            
            for (int i = 0; i < cdfs.length; i++) {
                XYSeries series = new XYSeries(names[i]);
                for (int j = 0; j < cdfs[i].length; j++) {
                    series.add(j * timeStep, cdfs[i][j]);
                }
                dataset.addSeries(series);
            }

            // 2. Create Chart
            JFreeChart chart = ChartFactory.createXYLineChart(
                "CDF Comparison",          // Title
                "Time",                    // X-Axis Label
                "Cumulative Probability",  // Y-Axis Label
                dataset,
                PlotOrientation.VERTICAL,
                true,                      // Show Legend
                true,                      // Show Tooltips
                false                      // No URLs
            );
            
            // 3. Styling
            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
            
            // Dynamic Loop: Apply thickness to ALL series
            for (int i = 0; i < cdfs.length; i++) {
                plot.getRenderer().setSeriesStroke(i, new BasicStroke(2.0f));
            }

            // 4. Save as PNG (Using the passed fileName parameter)
            // Ensure extension exists
            if (!fileName.endsWith(".png")) {
                fileName += ".png";
            }
            
            File imageFile = new File(fileName);
            ChartUtils.saveChartAsPNG(imageFile, chart, 800, 600);
            
            System.out.println("Chart saved to: " + imageFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error saving chart: " + e.getMessage());
        }
    }

    /**
     * Same as {@link #saveCdfComparison(double[][], String[], double, String)}
     * @param analysis map of cdf arrays
     * @param timeSteps map of time steps
     * @param fileName name of the file where the image is saved
     */
    public static void saveCdfComparison(Map<String, double[]> analysis, Map<String, Double> timeSteps, String fileName) {
        // Safety check
        if (analysis.keySet().size() != timeSteps.keySet().size()) {
            System.err.println("Error: Number of CDF arrays must match number of timeSteps.");
            return;
        }
        try {
            // 1. Create Dataset
            XYSeriesCollection dataset = new XYSeriesCollection();
            
            int i = 0;
            for (String name : analysis.keySet()) {
                XYSeries series = new XYSeries(name);
                for (int j = 0; j < analysis.get(name).length; j++) {
                    series.add(j * timeSteps.get(name), analysis.get(name)[j]);
                }
                dataset.addSeries(series);
                i++;
            }

            // 2. Create Chart
            JFreeChart chart = ChartFactory.createXYLineChart(
                "CDF Comparison",          // Title
                "Time",                    // X-Axis Label
                "Cumulative Probability",  // Y-Axis Label
                dataset,
                PlotOrientation.VERTICAL,
                true,                      // Show Legend
                true,                      // Show Tooltips
                false                      // No URLs
            );
            
            // 3. Styling
            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
            
            // Dynamic Loop: Apply thickness to ALL series
            for (i = 0; i < analysis.keySet().size(); i++) {
                plot.getRenderer().setSeriesStroke(i, new BasicStroke(2.0f));
            }

            // 4. Save as PNG (Using the passed fileName parameter)
            // Ensure extension exists
            if (!fileName.endsWith(".png")) {
                fileName += ".png";
            }
            
            File imageFile = new File(fileName);
            ChartUtils.saveChartAsPNG(imageFile, chart, 800, 600);
            
            System.out.println("Chart saved to: " + imageFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error saving chart: " + e.getMessage());
        }
    }

    /**
     * It creates an image for the comparison of the cdfs using a map of {@link Analysis}.
     * @param analysis CDF values, the keys of the map are used to name the lines in the plot
     * @param fileName name of the file
     */
    public static void saveCdfComparison(Map<String, Analysis> analysis, String fileName) {
        try {
            // 1. Create Dataset
            XYSeriesCollection dataset = new XYSeriesCollection();
            
            int i = 0;
            for (String name : analysis.keySet()) {
                XYSeries series = new XYSeries(name);
                for (int j = 0; j < analysis.get(name).getValues().length; j++) {
                    series.add(j * analysis.get(name).getTimeStep(), analysis.get(name).getValues()[j]);
                }
                dataset.addSeries(series);
                i++;
            }

            // 2. Create Chart
            JFreeChart chart = ChartFactory.createXYLineChart(
                "CDF Comparison",          // Title
                "Time",                    // X-Axis Label
                "Cumulative Probability",  // Y-Axis Label
                dataset,
                PlotOrientation.VERTICAL,
                true,                      // Show Legend
                true,                      // Show Tooltips
                false                      // No URLs
            );
            
            // 3. Styling
            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
            
            // Dynamic Loop: Apply thickness to ALL series
            for (i = 0; i < analysis.keySet().size(); i++) {
                plot.getRenderer().setSeriesStroke(i, new BasicStroke(2.0f));
            }

            // 4. Save as PNG (Using the passed fileName parameter)
            // Ensure extension exists
            if (!fileName.endsWith(".png")) {
                fileName += ".png";
            }
            
            File imageFile = new File(fileName);
            ChartUtils.saveChartAsPNG(imageFile, chart, 800, 600);
            
            System.out.println("Chart saved to: " + imageFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error saving chart: " + e.getMessage());
        }
    }
}

