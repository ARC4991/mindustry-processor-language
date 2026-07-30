package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.codegen.MlogProgramMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        Objects.requireNonNull(preferences, "preferences");
        Objects.requireNonNull(memoryLayout, "memoryLayout");
        MlogProgramMetrics metrics = validateMetrics("Main", mlog, profile);
        TargetProfile.ProcessorKind processor = chooseProcessor(profile, preferences, preferences.processors());
        validateMemoryLimits(memoryLayout, preferences);
        RuntimePlan plan = new RuntimePlan(processor, metrics.instructions(), metrics.labels(),
            metrics.maxTokensPerStatement(), virtualSlots(mlog), memoryLayout);
        log.info("自动运行时规划：processor={}, instructions={}, labels={}, virtualSlots={}, physicalSlots={}, objectPoolSlots={}, stringSlots={}",
            plan.processorId(), plan.instructions(), plan.labels(), plan.virtualSlots(), plan.physicalSlots(),
            plan.objectPoolSlots(), plan.stringSlots());
        return plan;
    }

    /** Plans already separated processor programs while enforcing global processor and Memory limits. */
    public RuntimeTopologyPlan planTopology(List<ShardSource> sources, TargetProfile profile,
                                            RuntimePreferences preferences,
                                            PhysicalMemoryLayout memoryLayout) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(preferences, "preferences");
        Objects.requireNonNull(memoryLayout, "memoryLayout");
        if (sources.isEmpty()) throw new IllegalArgumentException("多处理器规划至少需要一个 shard 源程序");
        validateMemoryLimits(memoryLayout, preferences);
        Map<TargetProfile.ProcessorKind, Integer> remaining = new EnumMap<>(TargetProfile.ProcessorKind.class);
        remaining.putAll(preferences.processors());
        List<ShardPlan> shards = new ArrayList<>();
        for (ShardSource source : sources) {
            MlogProgramMetrics metrics = validateMetrics(source.id(), source.mlog(), profile);
            TargetProfile.ProcessorKind processor = chooseProcessor(profile, preferences, remaining);
            remaining.compute(processor, (ignored, count) -> count == null ? 0 : count - 1);
            shards.add(new ShardPlan(source.id(), source.roles(), processor, metrics.instructions(), metrics.labels(),
                metrics.maxTokensPerStatement(), virtualSlots(source.mlog())));
        }
        RuntimeTopologyPlan plan = new RuntimeTopologyPlan(shards, memoryLayout);
        log.info("自动多处理器规划：shards={}, instructions={}, labels={}, virtualSlots={}, physicalSlots={}",
            plan.shards().size(), plan.instructions(), plan.labels(), plan.virtualSlots(), plan.physicalSlots());
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

    private TargetProfile.ProcessorKind chooseProcessor(TargetProfile profile, RuntimePreferences preferences,
                                                        Map<TargetProfile.ProcessorKind, Integer> available) {
        List<TargetProfile.ProcessorKind> candidates = available.entrySet().stream()
            .filter(entry -> entry.getValue() > 0).map(java.util.Map.Entry::getKey).toList();
        if (candidates.isEmpty()) throw new IllegalArgumentException("Runtime 允许的处理器数量不足以容纳全部 shard");
        if (preferences.goal() == RuntimePreferences.Goal.MAX_PERFORMANCE) {
            return candidates.stream().max(java.util.Comparator.comparingInt(profile::instructionsPerTick)).orElseThrow();
        }
        // The game resumes the same instruction stream on later ticks. IPT is
        // throughput, while maxInstructions is the parser's program-size limit.
        return candidates.stream().min(java.util.Comparator.comparingInt(profile::instructionsPerTick)).orElseThrow();
    }

    private MlogProgramMetrics validateMetrics(String shard, String mlog, TargetProfile profile) {
        MlogProgramMetrics metrics = MlogProgramMetrics.analyze(Objects.requireNonNull(mlog, "mlog"));
        if (metrics.instructions() > profile.maxInstructions()) {
            throw new IllegalArgumentException("shard " + shard + " 含 " + metrics.instructions()
                + " 条指令，超过 target " + profile.id() + " 的 " + profile.maxInstructions() + " 条上限");
        }
        if (metrics.labels() > profile.maxJumpLabels()) {
            throw new IllegalArgumentException("shard " + shard + " 含 " + metrics.labels()
                + " 个标签，超过 target " + profile.id() + " 的 " + profile.maxJumpLabels() + " 个上限");
        }
        if (metrics.maxTokensPerStatement() > profile.maxTokensPerStatement()) {
            throw new IllegalArgumentException("shard " + shard + " 的单条指令含 "
                + metrics.maxTokensPerStatement() + " 个 token，超过 target " + profile.id()
                + " 的 " + profile.maxTokensPerStatement() + " 个上限");
        }
        return metrics;
    }

    private int virtualSlots(String mlog) {
        Set<String> variables = new HashSet<>();
        for (String raw : mlog.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.endsWith(":")) continue;
            for (String token : line.split("\\s+")) {
                if (token.startsWith("mpl_") || token.startsWith("__mpl_")) variables.add(token);
            }
        }
        return variables.size();
    }

    public record ShardSource(String id, List<String> roles, String mlog) {
        public ShardSource {
            if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("无效的 shard 源程序 id：" + id);
            }
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            Objects.requireNonNull(mlog, "mlog");
        }
    }
}
