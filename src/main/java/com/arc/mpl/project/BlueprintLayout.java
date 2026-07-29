package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic positions shared by schematic serialization and deployment guidance. */
public record BlueprintLayout(int width, int height, List<ShardPlacement> shards,
                              List<MemoryPlacement> memories) {
    public BlueprintLayout {
        if (width < 1 || height < 1) throw new IllegalArgumentException("蓝图尺寸必须为正数");
        shards = List.copyOf(Objects.requireNonNull(shards, "shards"));
        memories = List.copyOf(Objects.requireNonNull(memories, "memories"));
        if (shards.isEmpty()) throw new IllegalArgumentException("蓝图必须至少包含一个处理器 shard");
    }

    public static BlueprintLayout singleShard(RuntimePlan plan) {
        ShardPlacement main = new ShardPlacement("Main", plan.processorId(), List.of("main"), 1, 1);
        List<MemoryPlacement> memories = new ArrayList<>();
        int x = 4;
        for (PhysicalMemoryLayout.Segment segment : plan.physicalMemoryLayout().segments()) {
            memories.add(new MemoryPlacement(segment, x, 1));
            x += segment.kind() == RuntimePreferences.MemoryKind.CELL ? 2 : 3;
        }
        return new BlueprintLayout(Math.max(3, x), 3, List.of(main), memories);
    }

    public ShardPlacement main() {
        return shards.stream().filter(shard -> shard.roles().contains("main")).findFirst().orElseThrow();
    }

    public record ShardPlacement(String id, String processor, List<String> roles, int x, int y) {
        public ShardPlacement {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(processor, "processor");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            if (x < 0 || y < 0) throw new IllegalArgumentException("处理器蓝图坐标不得为负数");
        }
    }

    public record MemoryPlacement(PhysicalMemoryLayout.Segment segment, int x, int y) {
        public MemoryPlacement {
            Objects.requireNonNull(segment, "segment");
            if (x < 0 || y < 0) throw new IllegalArgumentException("Memory 蓝图坐标不得为负数");
        }
    }
}
