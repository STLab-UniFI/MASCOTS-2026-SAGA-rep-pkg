package it.unifi.dinfo.stlab.modeling.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unifi.dinfo.stlab.modeling.Analysis;
import it.unifi.dinfo.stlab.modeling.AnalysisUtils;
import it.unifi.dinfo.stlab.modeling.Constants;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.modeling.utils.ScenarioInfo;

public class SagaScenarioAnalyzer {

    public List<ScenarioInfo> getGroupedScenarios(List<ScenarioInfo> allScenarios, boolean checkProbabilitySum) {
        List<ScenarioInfo> resultScenarios = new ArrayList<>();
        
        ScenarioInfo happyPathScenario = this.getHappyPathScenario(allScenarios);
        resultScenarios.add(happyPathScenario);
        
        Set<SimpleTask> uniquesFailedTask = new HashSet<>();
        for (ScenarioInfo scenario : allScenarios) {
            if (!scenario.isHappyPath()) {
                uniquesFailedTask.add(scenario.getFailingService());
            }
        }

        for (SimpleTask failedTask : uniquesFailedTask) {
            resultScenarios.add(this.groupByTask(allScenarios, failedTask));
        }

        if (checkProbabilitySum)
            checkProbabilitySumToOne(resultScenarios);

        return resultScenarios;
    }

    private static void checkProbabilitySumToOne(List<ScenarioInfo> allScenarios) {
        double sum = 0.0;
        for (ScenarioInfo scenario : allScenarios) {
            sum += scenario.getProbability();
        }

        if (Math.abs(sum - 1.0) > Constants.ERROR_REGENERATIVE_ANALYSIS_ALLOWED)
            throw new RuntimeException("The sum of the probabilities of the scenarios is not equal to 1! Sum: " + sum);
    }

    private ScenarioInfo getHappyPathScenario(List<ScenarioInfo> allScenarios) {
        ScenarioInfo happyPathScenario = null;
        for (ScenarioInfo scenario : allScenarios) {
            if (scenario.isHappyPath())
                happyPathScenario = scenario;
        }

        if (happyPathScenario == null)
            throw new RuntimeException("No happy path found");

        return happyPathScenario;
    }
    

    /**
     * This function create a scenario where the analysis is the mixture of the analysis
     * @param allScenarios
     * @param task
     * @return
     */
    public ScenarioInfo groupByTask(List<ScenarioInfo> allScenarios, SimpleTask task) {
        Map<Analysis, Double> groupedAnalysis = new HashMap<>();
        boolean noScenarioHasThisFailingTask = true;

        double sumOfProbabilities = 0.0;
        for (ScenarioInfo scenarioInfo : allScenarios) {
            if (scenarioInfo.isAggregable()) {
                if (scenarioInfo.getFailingService().equals(task)) {
                    noScenarioHasThisFailingTask = false;
                    groupedAnalysis.put(scenarioInfo.getAnalysis(), scenarioInfo.getProbability());
                    sumOfProbabilities += scenarioInfo.getProbability();
                }
            }
        }

        if (noScenarioHasThisFailingTask)
            return null;
        
        for (Analysis analysis : groupedAnalysis.keySet())
            groupedAnalysis.put(analysis, groupedAnalysis.get(analysis) / sumOfProbabilities);

        Analysis mixture = mixture(groupedAnalysis);
        return ScenarioInfo.aggregateScenario(mixture, sumOfProbabilities, task);
    }

    /**
     * This function calculates the mixture of multiple CDFs.
     * It first checks if the analysis have the same time limit and time step. 
     * If this is not the case the mixture can't be done. In future we can use interpolation
     * do calculate the mixture even with different time limit and time step.
     * @param probabilityMap a map that connects each CDF with its respective probability
     * @return the mixture of the CDFs
     */
    public static Analysis mixture(Map<Analysis, Double> probabilityMap) {
        if (probabilityMap == null)
            throw new RuntimeException("ProbabilityMap is null");
        if (!doesAnalysisHaveSameTimeLimitTimeStep(new ArrayList<>(probabilityMap.keySet()))) 
            throw new RuntimeException("Time limit or time step of the analysis are different!");

        Analysis randomAnalysis = new ArrayList<>(probabilityMap.keySet()).get(0);
        double[] totalCdf = new double[randomAnalysis.getValues().length];

        for (Analysis a : probabilityMap.keySet()) {
            double[] actualValues = a.getValues();
            for (int i = 0; i < totalCdf.length; i++)
                totalCdf[i] += probabilityMap.get(a) * actualValues[i];
        }

        return new Analysis(totalCdf, randomAnalysis.getTimeLimit(), randomAnalysis.getTimeStep());
    }

    public static Analysis mixture(List<ScenarioInfo> scenarios, boolean normalize) {
        Map<Analysis, Double> probabilityMap = new HashMap<>();

        for (ScenarioInfo scenario : scenarios) {
            probabilityMap.put(scenario.getAnalysis(), scenario.getProbability());
        }

        if (normalize)
            probabilityMap = normalizeProbabilities(probabilityMap);
        else
            checkProbabilitySumToOne(scenarios);
    
        return SagaScenarioAnalyzer.mixture(probabilityMap);
    }

    private static Map<Analysis, Double> normalizeProbabilities(Map<Analysis, Double> analysis) {
        double sumOfProbabilities = analysis.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<Analysis, Double> normalizedMap = new HashMap<>();
        for (Map.Entry<Analysis, Double> entry : analysis.entrySet()) {
            normalizedMap.put(entry.getKey(), entry.getValue() / sumOfProbabilities);
        }
        System.out.println(">>>>>> Probabilities normalized! Sum: " + normalizedMap.values().stream().mapToDouble(Double::doubleValue).sum());
        return normalizedMap;
    }

    public static List<ScenarioInfo> interpolateAllScenarios(List<ScenarioInfo> scenarios) {
        List<ScenarioInfo> scenariosInterpolated = new ArrayList<>();

        double maxTimeLimit = Double.MIN_VALUE;
        double minTimeStep = Double.MAX_VALUE;

        for (ScenarioInfo scenario : scenarios) {
            if (scenario.getAnalysis().getTimeLimit() > maxTimeLimit) 
                maxTimeLimit = scenario.getAnalysis().getTimeLimit();
            if (scenario.getAnalysis().getTimeStep() < minTimeStep)
                minTimeStep = scenario.getAnalysis().getTimeStep();
        }

        for (ScenarioInfo scenario : scenarios) {
            scenariosInterpolated.add(
                scenario.resample(maxTimeLimit, minTimeStep)
            );
        }

        return scenariosInterpolated;
    }

    private static boolean doesAnalysisHaveSameTimeLimitTimeStep(List<Analysis> analysis) {
        boolean condition = true;

        double timeLimit = analysis.get(0).getTimeLimit();
        double timeStep = analysis.get(0).getTimeStep();
        
        for (Analysis a : analysis) {
            if (timeLimit != a.getTimeLimit() || timeStep != a.getTimeStep()) {
                condition = false;
                break;
            }
        }

        return condition;
    }
}
