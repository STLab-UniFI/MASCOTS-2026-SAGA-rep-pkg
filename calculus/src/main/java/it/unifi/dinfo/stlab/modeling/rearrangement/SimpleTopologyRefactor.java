package it.unifi.dinfo.stlab.modeling.rearrangement;

import it.unifi.dinfo.stlab.modeling.CompositeTask;
import it.unifi.dinfo.stlab.modeling.SagaTask;
import it.unifi.dinfo.stlab.modeling.SimpleTask;
import it.unifi.dinfo.stlab.variableStochasticTime.VariableDeterministicTime;

import java.util.Comparator;

public class SimpleTopologyRefactor {

    public static SagaTask maxParallelism(SagaTask task) {
        return SagaTask.and("maxParallelism", task.getSimpleTasks().toArray(new SimpleTask[0]));
    }

    public static SagaTask sequential(SagaTask task) {
        return SagaTask.seq("allSequence", task.getSimpleTasks().toArray(new SimpleTask[0]));
    }

    public static SagaTask sequentialFailLast(SagaTask task) {
        SimpleTask[] sortedTasks = task.getSimpleTasks().stream()
                .sorted(Comparator.comparingDouble(SimpleTask::getProbToFail))
                .toArray(SimpleTask[]::new);
        return SagaTask.seq("allSequence", sortedTasks);
    }

    public static SagaTask sequentialFailFirst(SagaTask task) {
        SimpleTask[] sortedTasks = task.getSimpleTasks().stream()
                .sorted(Comparator.comparingDouble(SimpleTask::getProbToFail).reversed())
                .toArray(SimpleTask[]::new);
        return SagaTask.seq("allSequence", sortedTasks);
    }


    public static void main(String[] args) {

        SimpleTask s1 = SagaTask.simple("s1", new VariableDeterministicTime(1), new VariableDeterministicTime(1),new VariableDeterministicTime(1),   0.9);
        SimpleTask s2 = SagaTask.simple("s2", new VariableDeterministicTime(1), new VariableDeterministicTime(1),new VariableDeterministicTime(1), 0.2);
        SimpleTask s3 = SagaTask.simple("s3", new VariableDeterministicTime(10),new VariableDeterministicTime(1),new VariableDeterministicTime(1), 0.1);

        CompositeTask seq = SagaTask.seq("seq", s3, s2);
        CompositeTask and = SagaTask.and("and", seq, s1);

        SagaTask refactored = SimpleTopologyRefactor.sequentialFailFirst(and);

        refactored.exportTopologyJson("./rearrange-topology.json");
    }


}
