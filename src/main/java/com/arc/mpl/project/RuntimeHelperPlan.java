package com.arc.mpl.project;

import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.MplType;
import com.arc.mpl.memory.SharedRuntimeLayoutPlanner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Stable compiler-private mapping from pure numeric functions to one Worker task protocol. */
public record RuntimeHelperPlan(List<Worker> workers, Map<String, Task> tasks) {
    public RuntimeHelperPlan {
        workers = List.copyOf(Objects.requireNonNull(workers, "workers"));
        tasks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(tasks, "tasks")));
        if (workers.isEmpty() != tasks.isEmpty()) {
            throw new IllegalArgumentException("helper Worker 与任务必须同时为空或非空");
        }
        if (workers.stream().map(Worker::id).distinct().count() != workers.size()) {
            throw new IllegalArgumentException("helper Worker id 不能重复");
        }
        for (Task task : tasks.values()) {
            if (!workers.stream().anyMatch(worker -> worker.id().equals(task.worker()))) {
                throw new IllegalArgumentException("helper 任务引用未知 Worker：" + task.function());
            }
        }
    }

    public static RuntimeHelperPlan empty() {
        return new RuntimeHelperPlan(List.of(), Map.of());
    }

    public boolean enabled() {
        return !workers.isEmpty();
    }

    public Optional<Task> task(String function) {
        return Optional.ofNullable(tasks.get(function));
    }

    public List<SharedRuntimeLayoutPlanner.MailboxRequirement> mailboxRequirements() {
        return workers.stream().flatMap(worker -> java.util.stream.Stream.of(
            new SharedRuntimeLayoutPlanner.MailboxRequirement(worker.requestMailbox(), "Main", worker.id(),
                worker.requestPayloadSlots()),
            new SharedRuntimeLayoutPlanner.MailboxRequirement(worker.responseMailbox(), worker.id(), "Main", 1)
        )).toList();
    }

    public record Worker(String id, String requestMailbox, String responseMailbox,
                         int requestPayloadSlots, List<String> functions) {
        public Worker {
            if (id == null || !id.matches("Worker-[0-9]+")) throw new IllegalArgumentException("无效的 helper Worker id：" + id);
            if (requestMailbox == null || !requestMailbox.matches("[A-Za-z][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("无效的请求邮箱 id：" + requestMailbox);
            }
            if (responseMailbox == null || !responseMailbox.matches("[A-Za-z][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("无效的响应邮箱 id：" + responseMailbox);
            }
            if (requestMailbox.equals(responseMailbox)) throw new IllegalArgumentException("请求与响应邮箱不能相同");
            if (requestPayloadSlots < 0) throw new IllegalArgumentException("请求 payload 宽度不得为负数");
            functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
            if (functions.isEmpty() || functions.stream().distinct().count() != functions.size()) {
                throw new IllegalArgumentException("helper Worker 必须拥有不重复的函数");
            }
        }
    }

    public record Task(String function, String worker, int kind, List<MplType> parameterTypes,
                       MplType returnType) {
        public Task {
            Objects.requireNonNull(function, "function");
            Objects.requireNonNull(worker, "worker");
            if (kind < 1 || kind > 2_000_000_000) throw new IllegalArgumentException("helper task kind 超出范围：" + kind);
            parameterTypes = List.copyOf(Objects.requireNonNull(parameterTypes, "parameterTypes"));
            Objects.requireNonNull(returnType, "returnType");
        }

        static Task from(HirFunction function, String worker, int kind) {
            return new Task(function.name(), worker, kind,
                function.parameters().stream().map(parameter -> parameter.type()).toList(), function.returnType());
        }
    }

    static RuntimeHelperPlan singleWorker(List<HirFunction> functions) {
        if (functions.isEmpty()) return empty();
        String worker = "Worker-0";
        Map<String, Task> tasks = new LinkedHashMap<>();
        int kind = 1;
        for (HirFunction function : functions) tasks.put(function.name(), Task.from(function, worker, kind++));
        int requestWidth = functions.stream().mapToInt(function -> function.parameters().size()).max().orElse(0);
        Worker workerPlan = new Worker(worker, "MainToWorker0", "Worker0ToMain", requestWidth,
            functions.stream().map(HirFunction::name).toList());
        return new RuntimeHelperPlan(List.of(workerPlan), tasks);
    }
}
