package it.unifi.dinfo.stlab.generation.distributionTemplates;

public class IoDistributionTemplate {
    private double lambda1;
    private double lambda2;
    private double mean_y;
    private double coefficient_of_variation;
    private int nLines;
    
    public double getLambda1() {
        return lambda1;
    }
    public void setLambda1(double lambda1) {
        this.lambda1 = lambda1;
    }
    public double getLambda2() {
        return lambda2;
    }
    public void setLambda2(double lambda2) {
        this.lambda2 = lambda2;
    }
    public double getMean_y() {
        return mean_y;
    }
    public void setMean_y(double mean_y) {
        this.mean_y = mean_y;
    }
    public double getCoefficient_of_variation() {
        return coefficient_of_variation;
    }
    public void setCoefficient_of_variation(double coefficient_of_variation) {
        this.coefficient_of_variation = coefficient_of_variation;
    }
    public int getnLines() {
        return nLines;
    }
    public void setnLines(int nLines) {
        this.nLines = nLines;
    }
}
