package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selects the smallest processor that can finish the current generated program in one IPT burst. */
@Slf4j
public final class RuntimePlanner {
    public RuntimePlan plan(String mlog, TargetProfile profile) {
        return plan(mlog, profile, RuntimePreferences.defaults());
    }

    public RuntimePlan plan(String mlog, TargetProfile profile, RuntimePreferences preferences) {
        return plan(mlog, profile, preferences, 0);
    }

    /** Future lowerings pass their proven physical-slot need here; source code never does. */
    public RuntimePlan plan(String mlog, TargetProfile profile, RuntimePreferences preferences, int physicalSlots) {
        int instructions = 0;
        int labels = 0;
        int maximumTokens = 0;
        Set<String> variables = new HashSet<>();
        for (String raw : mlog.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (line.endsWith(":")) { labels++; continue; }
            String[] tokens = line.split("\\s+");
            instructions++;
            maximumTokens = Math.max(maximumTokens, tokens.length);
            for (String token : tokens) {
                if (token.startsWith("mpl_") || token.startsWith("__mpl_")) variables.add(token);
            }
        }
        TargetProfile.ProcessorKind processor = chooseProcessor(instructions, profile, preferences);
        MemoryLayout memory = memoryLayout(physicalSlots, profile, preferences);
        RuntimePlan plan = new RuntimePlan(processor, instructions, labels, maximumTokens, variables.size(), physicalSlots,
            memory.cells(), memory.banks());
        log.info("自动运行时规划：processor={}, instructions={}, labels={}, virtualSlots={}, physicalSlots={}",
            plan.processorId(), plan.instructions(), plan.labels(), plan.virtualSlots(), plan.physicalSlots());
        return plan;
    }

    private MemoryLayout memoryLayout(int slots, TargetProfile profile, RuntimePreferences preferences) {
        if (slots == 0) return new MemoryLayout(0, 0);
        int remaining = slots, cells = 0, banks = 0;
        for (RuntimePreferences.MemoryKind kind : java.util.List.of(RuntimePreferences.MemoryKind.BANK, RuntimePreferences.MemoryKind.CELL)) {
            if (preferences.memory().getOrDefault(kind, 0) == 0) continue;
            int capacity = kind == RuntimePreferences.MemoryKind.CELL ? profile.memoryCellCapacity() : profile.memoryBankCapacity();
            int count = (remaining + capacity - 1) / capacity;
            if (count > preferences.memory().getOrDefault(kind, 0)) continue;
            return kind == RuntimePreferences.MemoryKind.CELL ? new MemoryLayout(count, 0) : new MemoryLayout(0, count);
        }
        throw new IllegalArgumentException("运行时 Memory 约束无法满足 " + slots + " 个物理槽需求");
    }

    private TargetProfile.ProcessorKind chooseProcessor(int instructions, TargetProfile profile, RuntimePreferences preferences) {
        List<TargetProfile.ProcessorKind> candidates = preferences.processors().entrySet().stream()
            .filter(entry -> entry.getValue() > 0).map(java.util.Map.Entry::getKey).toList();
        if (preferences.goal() == RuntimePreferences.Goal.MAX_PERFORMANCE) {
            return candidates.stream().max(java.util.Comparator.comparingInt(profile::instructionsPerTick)).orElseThrow();
        }
        return candidates.stream().filter(kind -> instructions <= profile.instructionsPerTick(kind))
            .min(java.util.Comparator.comparingInt(profile::instructionsPerTick))
            .orElseGet(() -> candidates.stream().max(java.util.Comparator.comparingInt(profile::instructionsPerTick)).orElseThrow());
    }

    private record MemoryLayout(int cells, int banks) { }
}
