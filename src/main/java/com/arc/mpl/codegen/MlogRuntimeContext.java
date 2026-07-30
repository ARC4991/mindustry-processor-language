package com.arc.mpl.codegen;

import com.arc.mpl.memory.SharedRuntimeLayout;
import com.arc.mpl.project.RuntimeHelperPlan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Identifies one generated processor program and its compiler-private shared startup protocol. */
public record MlogRuntimeContext(String shardId, Optional<SharedRuntimeLayout> sharedRuntime,
                                 RuntimeHelperPlan helperPlan) {
    public MlogRuntimeContext {
        if (shardId == null || !shardId.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("无效的 mlog shard id：" + shardId);
        }
        sharedRuntime = sharedRuntime == null ? Optional.empty() : sharedRuntime;
        helperPlan = helperPlan == null ? RuntimeHelperPlan.empty() : helperPlan;
        sharedRuntime.ifPresent(layout -> {
            if (!layout.mainShard().equals(shardId) && !layout.workers().contains(shardId)) {
                throw new IllegalArgumentException("shard 不属于共享 Runtime：" + shardId);
            }
        });
        if (helperPlan.enabled()) {
            SharedRuntimeLayout layout = sharedRuntime.orElseThrow(() ->
                new IllegalArgumentException("helper 计划需要共享 Runtime 布局"));
            List<String> plannedWorkers = helperPlan.workers().stream().map(RuntimeHelperPlan.Worker::id).toList();
            if (!layout.workers().equals(plannedWorkers)) {
                throw new IllegalArgumentException("helper Worker 与共享 Runtime 拓扑不一致");
            }
            helperPlan.mailboxRequirements().forEach(requirement -> {
                var mailbox = layout.mailbox(requirement.id());
                if (!mailbox.producer().equals(requirement.producer())
                    || !mailbox.consumer().equals(requirement.consumer())
                    || mailbox.payloadSlots() != requirement.payloadSlots()) {
                    throw new IllegalArgumentException("helper 邮箱与共享 Runtime 布局不一致：" + requirement.id());
                }
            });
        }
    }

    public MlogRuntimeContext(String shardId, Optional<SharedRuntimeLayout> sharedRuntime) {
        this(shardId, sharedRuntime, RuntimeHelperPlan.empty());
    }

    public static MlogRuntimeContext singleShard() {
        return new MlogRuntimeContext("Main", Optional.empty(), RuntimeHelperPlan.empty());
    }

    public static MlogRuntimeContext shared(String shardId, SharedRuntimeLayout layout) {
        return shared(shardId, layout, RuntimeHelperPlan.empty());
    }

    public static MlogRuntimeContext shared(String shardId, SharedRuntimeLayout layout,
                                            RuntimeHelperPlan helperPlan) {
        return new MlogRuntimeContext(shardId, Optional.of(Objects.requireNonNull(layout, "layout")), helperPlan);
    }

    public boolean main() {
        return sharedRuntime.map(layout -> layout.mainShard().equals(shardId)).orElse(true);
    }

    public boolean worker() {
        return !main();
    }
}
