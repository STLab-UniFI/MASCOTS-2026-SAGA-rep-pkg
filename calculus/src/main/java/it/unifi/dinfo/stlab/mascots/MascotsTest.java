package it.unifi.dinfo.stlab.mascots;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.oristool.eulero.evaluation.approximator.TruncatedExponentialApproximation;
import org.oristool.eulero.evaluation.approximator.TruncatedExponentialMixtureApproximation;
import org.oristool.eulero.evaluation.heuristics.AnalysisHeuristicsVisitor;
import org.oristool.eulero.evaluation.heuristics.SDFHeuristicsVisitor;
import org.oristool.eulero.modeling.Activity;
import org.oristool.eulero.modeling.ModelFactory;
import org.oristool.eulero.modeling.Simple;
import org.oristool.eulero.modeling.stochastictime.GeneralizeErlangTime;
import org.oristool.util.xpnCreation.XpnGenerator;

import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.modeling.utils.ScenarioInfo;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableDeterministicTime;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableExponentialTime;

public class MascotsTest {
    private SagaTask testTopologyDeterministic() {
        // Create the following topology:
        // AND(SEQ(s1, s2), SEQ(s3, s4), XOR(s5, s6))
        SimpleTask s1 = SagaTask.simple("s1", new VariableDeterministicTime(500.), 0.0);
        SimpleTask s2 = SagaTask.simple("s2", new VariableDeterministicTime(500.), 0.5);
        SimpleTask  s3 = SagaTask.simple("s3", new VariableDeterministicTime(500.), 0.0);
        SimpleTask  s4 = SagaTask.simple("s4", new VariableDeterministicTime(500.), 0.5);
        SimpleTask  s5 = SagaTask.simple("s5", new VariableDeterministicTime(500.), 0.0);
        SimpleTask  s6 = SagaTask.simple("s6", new VariableDeterministicTime(500.), 0.0);

        SagaTask seq1 = SagaTask.seq("seq1", s1, s2);
        SagaTask seq2 = SagaTask.seq("seq2", s3, s4);
        SagaTask xor = SagaTask.xor("xor", List.of(s5, s6), List.of(0.4, 0.6));
        SagaTask root = SagaTask.and("and", seq1, seq2, xor);
        return root;
    }

    private SagaTask testTopologyExponential() {
        // Create the following topology:
        // AND(SEQ(s1, s2), SEQ(s3, s4), XOR(s5, s6))
        SimpleTask s1 = SagaTask.simple("s1",  new VariableExponentialTime(1./250.), 0.1);
        SimpleTask s2 = SagaTask.simple("s2",  new VariableExponentialTime(1./250.), 0.1);
        SimpleTask  s3 = SagaTask.simple("s3", new VariableExponentialTime(1./250.), 0.1);
        SimpleTask  s4 = SagaTask.simple("s4", new VariableExponentialTime(1./250.), 0.1);
        SimpleTask  s5 = SagaTask.simple("s5", new VariableExponentialTime(1./250.), 0.1);
        SimpleTask  s6 = SagaTask.simple("s6", new VariableExponentialTime(1./250.), 0.1);

        SagaTask seq1 = SagaTask.seq("seq1", s1, s2);
        SagaTask seq2 = SagaTask.seq("seq2", s3, s4);
        SagaTask xor = SagaTask.xor("xor", List.of(s5, s6), List.of(0.4, 0.6));
        SagaTask root = SagaTask.and("and", seq1, seq2, xor);
        return root;
    }

    private void saveCsv(SagaTask root, String outputPath) throws IOException {
        List<ScenarioInfo> allScenarios = root.calculateAllScenarioAnalysis();
        for (ScenarioInfo scenario : allScenarios) {
            System.out.println(scenario);
        }
        ScenarioInfo.writeCsvs(allScenarios, outputPath);
    }

    private static void testGeneralizedErlang() {
        Activity a = ModelFactory.sequence(
            new Simple("s-1", new GeneralizeErlangTime(67, new BigDecimal(14), new BigDecimal(9)))
        );

        AnalysisHeuristicsVisitor analyzer = new SDFHeuristicsVisitor(BigInteger.valueOf(2), BigInteger.valueOf(5), new TruncatedExponentialMixtureApproximation());

        double[] solution = a.analyze(new BigDecimal(100), new BigDecimal(0.1), analyzer);

        System.out.println(solution[solution.length-1]);
    }

    public static void main(String[] args) throws IOException {
        testGeneralizedErlang();
        /*
        MascotsYamlGenerator generator = new MascotsYamlGenerator();
        SagaTask root = new MascotsTest().testTopologyExponential();
        //generator.generateYamlFiles(root, "/home/tommaso/MASCOTS26/experimentation/testTopologyExponential");
        new XpnGenerator(root.getStpnModel()).saveToFile("./testExponential.xpn");
        System.out.println(root.getAllRewardCombinations());
        new MascotsTest().saveCsv(root, "/home/tommaso/MASCOTS26/experimentation/csvExponential");
        */
    }
}
