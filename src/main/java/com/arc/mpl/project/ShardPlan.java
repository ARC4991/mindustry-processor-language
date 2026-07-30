package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;

import java.util.List;
import java.util.Objects;

/** Immutable resource and role plan for one generated processor program. */
public record ShardPlan(String id, List<String> roles, TargetProfile.ProcessorKind processor,
                        int instructions, int labels, int maxTokensPerStatement, int virtualSlots) {
    public ShardPlan {
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("无效的 shard id：" + id);
        }
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        if (roles.isEmpty() || roles.stream().anyMatch(role -> role == null || role.isBlank())) {
            throw new IllegalArgumentException("shard 至少需要一个非空角色：" + id);
        }
        if (roles.stream().distinct().count() != roles.size()) {
            throw new IllegalArgumentException("shard 角色不能重复：" + id);
        }
        Objects.requireNonNull(processor, "processor");
        if (instructions < 0 || labels < 0 || maxTokensPerStatement < 0 || virtualSlots < 0) {
            throw new IllegalArgumentException("shard 资源计数不得为负数：" + id);
        }
    }

    public String processorId() {
        return switch (processor) {
            case MICRO -> "micro";
            case LOGIC -> "logic";
            case HYPER -> "hyper";
        };
    }

    public static ShardPlan main(RuntimePlan plan) {
        Objects.requireNonNull(plan, "plan");
        return new ShardPlan("Main", List.of("main"), plan.processor(), plan.instructions(), plan.labels(),
            plan.maxTokensPerStatement(), plan.virtualSlots());
    }
}
