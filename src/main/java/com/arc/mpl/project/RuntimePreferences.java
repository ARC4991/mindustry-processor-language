package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;

import java.util.Map;

/** User-owned bounds for automatic runtime layout; coordinates and aliases remain compiler-owned. */
public record RuntimePreferences(
    Goal goal,
    Map<TargetProfile.ProcessorKind, Integer> processors,
    Map<MemoryKind, Integer> memory
) {
    public RuntimePreferences {
        processors = Map.copyOf(processors);
        memory = Map.copyOf(memory);
        if (processors.values().stream().noneMatch(value -> value > 0)) throw new IllegalArgumentException("运行时至少要允许一种处理器");
        if (memory.values().stream().noneMatch(value -> value > 0)) throw new IllegalArgumentException("运行时至少要允许一种 Memory");
    }

    public static RuntimePreferences defaults() {
        return new RuntimePreferences(Goal.MIN_RESOURCES, Map.of(
            TargetProfile.ProcessorKind.MICRO, Integer.MAX_VALUE,
            TargetProfile.ProcessorKind.LOGIC, Integer.MAX_VALUE,
            TargetProfile.ProcessorKind.HYPER, Integer.MAX_VALUE), Map.of(
            MemoryKind.CELL, Integer.MAX_VALUE, MemoryKind.BANK, Integer.MAX_VALUE));
    }

    public enum Goal { MIN_RESOURCES, BALANCED, MAX_PERFORMANCE }
    public enum MemoryKind { CELL, BANK }
}
