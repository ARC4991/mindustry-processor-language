package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.SharedRuntimeLayout;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deployment-wide processors and compiler-owned shared physical Memory. */
public record RuntimeTopologyPlan(List<ShardPlan> shards, PhysicalMemoryLayout physicalMemoryLayout,
                                  Optional<SharedRuntimeLayout> sharedRuntime) {
    public RuntimeTopologyPlan(List<ShardPlan> shards, PhysicalMemoryLayout physicalMemoryLayout) {
        this(shards, physicalMemoryLayout, Optional.empty());
    }

    public RuntimeTopologyPlan {
        shards = List.copyOf(Objects.requireNonNull(shards, "shards"));
        physicalMemoryLayout = Objects.requireNonNull(physicalMemoryLayout, "physicalMemoryLayout");
        sharedRuntime = sharedRuntime == null ? Optional.empty() : sharedRuntime;
        if (shards.isEmpty()) throw new IllegalArgumentException("Runtime 拓扑至少需要一个 shard");
        Set<String> ids = new LinkedHashSet<>();
        for (ShardPlan shard : shards) {
            if (!ids.add(shard.id())) throw new IllegalArgumentException("重复的 shard id：" + shard.id());
        }
        long mainCount = shards.stream().filter(shard -> shard.roles().contains("main")).count();
        if (mainCount != 1) throw new IllegalArgumentException("Runtime 拓扑必须且只能包含一个 main shard");
        if (!shards.get(0).roles().contains("main")) {
            throw new IllegalArgumentException("Main shard 必须位于拓扑首位以保持蓝图识别稳定");
        }
        if (sharedRuntime.isPresent()) {
            SharedRuntimeLayout shared = sharedRuntime.orElseThrow();
            String main = shards.stream().filter(shard -> shard.roles().contains("main"))
                .map(ShardPlan::id).findFirst().orElseThrow();
            List<String> workers = shards.stream().filter(shard -> !shard.id().equals(main))
                .map(ShardPlan::id).toList();
            if (!shared.mainShard().equals(main) || !shared.workers().equals(workers)) {
                throw new IllegalArgumentException("共享 Runtime 的 shard 身份与拓扑不一致");
            }
            if (!shared.header().equals(physicalMemoryLayout.allocations().get(SharedRuntimeLayout.storageKey()))) {
                throw new IllegalArgumentException("共享 Runtime header 不属于拓扑的物理 Memory 布局");
            }
        }
    }

    public static RuntimeTopologyPlan singleShard(RuntimePlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new RuntimeTopologyPlan(List.of(ShardPlan.main(plan)), plan.physicalMemoryLayout(), Optional.empty());
    }

    public ShardPlan main() {
        return shards.stream().filter(shard -> shard.roles().contains("main")).findFirst().orElseThrow();
    }

    public int instructions() {
        return Math.toIntExact(shards.stream().mapToLong(ShardPlan::instructions).sum());
    }

    public int labels() {
        return Math.toIntExact(shards.stream().mapToLong(ShardPlan::labels).sum());
    }

    public int virtualSlots() {
        return Math.toIntExact(shards.stream().mapToLong(ShardPlan::virtualSlots).sum());
    }

    public int physicalSlots() {
        return physicalMemoryLayout.physicalSlots();
    }

    public int objectPoolSlots() {
        return physicalMemoryLayout.objectPoolSlots();
    }

    public int stringSlots() {
        return physicalMemoryLayout.stringRuntime().slots();
    }

    public int runtimeSlots() {
        return sharedRuntime.map(SharedRuntimeLayout::slots).orElse(0);
    }
}
