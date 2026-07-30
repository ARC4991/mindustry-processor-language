package com.arc.mpl.project;

import com.arc.mpl.hir.TupleType;
import com.arc.mpl.hir.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeHelperPlanTest {
    @Test
    void rejectsTasksOwnedByTheWrongWorker() {
        List<RuntimeHelperPlan.Worker> workers = List.of(
            worker(0, 2, "add"), worker(1, 2, "subtract"));

        assertThrows(IllegalArgumentException.class, () -> new RuntimeHelperPlan(workers, Map.of(
            "add", task("add", "Worker-1", 1, List.of(ValueType.INT, ValueType.INT)),
            "subtract", task("subtract", "Worker-1", 2, List.of(ValueType.INT, ValueType.INT)))));
    }

    @Test
    void rejectsAnUndersizedMailboxAndNonScalarAbi() {
        RuntimeHelperPlan.Worker narrow = worker(0, 1, "add");

        assertThrows(IllegalArgumentException.class, () -> new RuntimeHelperPlan(List.of(narrow), Map.of(
            "add", task("add", "Worker-0", 1, List.of(ValueType.INT, ValueType.INT)))));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeHelperPlan.Task("tuple", "Worker-0", 1,
            List.of(new TupleType(List.of(ValueType.INT, ValueType.INT))), ValueType.INT));
    }

    private RuntimeHelperPlan.Worker worker(int index, int width, String function) {
        return new RuntimeHelperPlan.Worker("Worker-" + index, "MainToWorker" + index,
            "Worker" + index + "ToMain", width, List.of(function));
    }

    private RuntimeHelperPlan.Task task(String function, String worker, int kind,
                                        List<com.arc.mpl.hir.MplType> parameters) {
        return new RuntimeHelperPlan.Task(function, worker, kind, parameters, ValueType.INT);
    }
}
