package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.codegen.MlogProgramMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Selects a permitted processor by resource/performance preference, never confusing IPT with program capacity. */
@Slf4j
public final class RuntimePlanner {
    public RuntimePlan plan(String mlog, TargetProfile profile) {
        return plan(mlog, profile, RuntimePreferences.defaults());
    }

    public RuntimePlan plan(String mlog, TargetProfile profile, RuntimePreferences preferences) {
        return plan(mlog, profile, preferences, PhysicalMemoryLayout.empty());
    }

    /** Uses the exact compiler-owned Memory layout already referenced by generated mlog aliases. */
    public RuntimePlan plan(String mlog, TargetProfile profile, RuntimePreferences preferences,
                            PhysicalMemoryLayout memoryLayout) {
        MlogProgramMetrics metrics = MlogProgramMetrics.analyze(mlog);
        Set<String> variables = new HashSet<>();
        for (String raw : mlog.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (line.startsWith("#") || line.endsWith(":")) continue;
            String[] tokens = line.split("\\s+");
            for (String token : tokens) {
                if (token.startsWith("mpl_") || token.startsWith("__mpl_")) variables.add(token);
            }
        }
        if (metrics.instructions() > profile.maxInstructions()) {
            throw new IllegalArgumentException("生成程序含 " + metrics.instructions() + " 条指令，超过 target " + profile.id()
                + " 的 " + profile.maxInstructions() + " 条上限");
        }
        TargetProfile.ProcessorKind processor = chooseProcessor(metrics.instructions(), profile, preferences);
        validateMemoryLimits(memoryLayout, preferences);
        RuntimePlan plan = new RuntimePlan(processor, metrics.instructions(), metrics.labels(),
            metrics.maxTokensPerStatement(), variables.size(), memoryLayout);
        log.info("自动运行时规划：processor={}, instructions={}, labels={}, virtualSlots={}, physicalSlots={}, objectPoolSlots={}, stringSlots={}",
            plan.processorId(), plan.instructions(), plan.labels(), plan.virtualSlots(), plan.physicalSlots(),
            plan.objectPoolSlots(), plan.stringSlots());
        return plan;
    }

    private void validateMemoryLimits(PhysicalMemoryLayout layout, RuntimePreferences preferences) {
        for (RuntimePreferences.MemoryKind kind : RuntimePreferences.MemoryKind.values()) {
            long actual = layout.segments().stream().filter(segment -> segment.kind() == kind).count();
            int allowed = preferences.memory().getOrDefault(kind, 0);
            if (actual > allowed) {
                throw new IllegalArgumentException("物理 Memory 布局包含 " + actual + " 个 " + kind
                    + "，超过 runtime 允许的 " + allowed + " 个");
            }
        }
    }

    private TargetProfile.ProcessorKind chooseProcessor(int instructions, TargetProfile profile, RuntimePreferences preferences) {
        List<TargetProfile.ProcessorKind> candidates = preferences.processors().entrySet().stream()
            .filter(entry -> entry.getValue() > 0).map(java.util.Map.Entry::getKey).toList();
        if (preferences.goal() == RuntimePreferences.Goal.MAX_PERFORMANCE) {
            return candidates.stream().max(java.util.Comparator.comparingInt(profile::instructionsPerTick)).orElseThrow();
        }
        // The game resumes the same instruction stream on later ticks. IPT is
        // throughput, while maxInstructions is the parser's program-size limit.
        return candidates.stream().min(java.util.Comparator.comparingInt(profile::instructionsPerTick)).orElseThrow();
    }
}
