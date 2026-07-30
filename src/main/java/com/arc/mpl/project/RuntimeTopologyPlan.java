package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deployment-wide processors and compiler-owned shared physical Memory. */
public record RuntimeTopologyPlan(List<ShardPlan> shards, PhysicalMemoryLayout physicalMemoryLayout) {
    public RuntimeTopologyPlan {
        shards = List.copyOf(Objects.requireNonNull(shards, "shards"));
        physicalMemoryLayout = Objects.requireNonNull(physicalMemoryLayout, "physicalMemoryLayout");
        if (shards.isEmpty()) throw new IllegalArgumentException("Runtime 拓扑至少需要一个 shard");
        Set<String> ids = new LinkedHashSet<>();
        for (ShardPlan shard : shards) {
            if (!ids.add(shard.id())) throw new IllegalArgumentException("重复的 shard id：" + shard.id());
        }
        long mainCount = shards.stream().filter(shard -> shard.roles().contains("main")).count();
        if (mainCount != 1) throw new IllegalArgumentException("Runtime 拓扑必须且只能包含一个 main shard");
    }

    public static RuntimeTopologyPlan singleShard(RuntimePlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new RuntimeTopologyPlan(List.of(ShardPlan.main(plan)), plan.physicalMemoryLayout());
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
}
