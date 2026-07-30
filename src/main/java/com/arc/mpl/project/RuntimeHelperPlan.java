package com.arc.mpl.project;

import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.MplType;
import com.arc.mpl.memory.SharedRuntimeLayoutPlanner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Stable compiler-private mapping from pure numeric functions to Worker task protocols. */
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
        if (workers.stream().flatMap(worker -> java.util.stream.Stream.of(
            worker.requestMailbox(), worker.responseMailbox())).distinct().count() != workers.size() * 2L) {
            throw new IllegalArgumentException("helper 邮箱 id 不能重复");
        }
        Map<String, Worker> functionOwners = new LinkedHashMap<>();
        for (Worker worker : workers) {
            for (String function : worker.functions()) {
                if (functionOwners.putIfAbsent(function, worker) != null) {
                    throw new IllegalArgumentException("helper 函数不能属于多个 Worker：" + function);
                }
            }
        }
        if (!functionOwners.keySet().equals(tasks.keySet())) {
            throw new IllegalArgumentException("helper Worker 函数与任务集合不一致");
        }
        if (tasks.values().stream().map(Task::kind).distinct().count() != tasks.size()) {
            throw new IllegalArgumentException("helper task kind 不能重复");
        }
        tasks.forEach((name, task) -> {
            if (!name.equals(task.function())) throw new IllegalArgumentException("helper 任务名称与索引不一致：" + name);
            Worker owner = functionOwners.get(name);
            if (!owner.id().equals(task.worker())) {
                throw new IllegalArgumentException("helper 任务引用错误 Worker：" + name);
            }
            if (task.parameterTypes().size() > owner.requestPayloadSlots()) {
                throw new IllegalArgumentException("helper 请求邮箱宽度不足：" + name);
            }
        });
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
            if (parameterTypes.stream().anyMatch(type -> !scalar(type)) || !scalar(returnType)) {
                throw new IllegalArgumentException("helper ABI 只允许 Int、Float 或 Bool：" + function);
            }
        }

        static Task from(HirFunction function, String worker, int kind) {
            return new Task(function.name(), worker, kind,
                function.parameters().stream().map(parameter -> parameter.type()).toList(), function.returnType());
        }

        private static boolean scalar(MplType type) {
            return type == com.arc.mpl.hir.ValueType.INT || type == com.arc.mpl.hir.ValueType.FLOAT
                || type == com.arc.mpl.hir.ValueType.BOOL;
        }
    }

    static RuntimeHelperPlan partitioned(List<HirFunction> functions, List<List<HirFunction>> partitions) {
        if (functions.isEmpty()) return empty();
        Objects.requireNonNull(partitions, "partitions");
        if (partitions.isEmpty() || partitions.stream().anyMatch(List::isEmpty)) {
            throw new IllegalArgumentException("helper 分区不能为空");
        }
        Map<String, String> owners = new LinkedHashMap<>();
        List<Worker> workers = new java.util.ArrayList<>();
        for (int index = 0; index < partitions.size(); index++) {
            String worker = "Worker-" + index;
            List<HirFunction> partition = List.copyOf(partitions.get(index));
            for (HirFunction function : partition) {
                if (owners.putIfAbsent(function.name(), worker) != null) {
                    throw new IllegalArgumentException("helper 函数被重复分区：" + function.name());
                }
            }
            int requestWidth = partition.stream().mapToInt(function -> function.parameters().size()).max().orElse(0);
            workers.add(new Worker(worker, "MainToWorker" + index, "Worker" + index + "ToMain", requestWidth,
                partition.stream().map(HirFunction::name).toList()));
        }
        if (!owners.keySet().equals(functions.stream().map(HirFunction::name)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))) {
            throw new IllegalArgumentException("helper 分区没有恰好覆盖全部候选函数");
        }
        Map<String, Task> tasks = new LinkedHashMap<>();
        int kind = 1;
        for (HirFunction function : functions) {
            tasks.put(function.name(), Task.from(function, owners.get(function.name()), kind++));
        }
        return new RuntimeHelperPlan(workers, tasks);
    }
}
