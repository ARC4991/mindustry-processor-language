package com.arc.mpl.compiler;

import com.arc.mpl.project.RuntimeHelperPlan;
import com.arc.mpl.project.RuntimeTopologyPlan;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Final compiler output for a deployable multi-processor program. */
public record MultiShardCompilation(List<Shard> shards, RuntimeTopologyPlan topology,
                                    RuntimeHelperPlan helperPlan) {
    public MultiShardCompilation {
        shards = List.copyOf(Objects.requireNonNull(shards, "shards"));
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(helperPlan, "helperPlan");
        if (!helperPlan.enabled()) throw new IllegalArgumentException("多 shard 编译结果必须包含 helper 计划");
        List<String> ids = shards.stream().map(Shard::id).toList();
        if (ids.size() != new LinkedHashSet<>(ids).size()) throw new IllegalArgumentException("重复的编译 shard id");
        if (!ids.equals(topology.shards().stream().map(value -> value.id()).toList())) {
            throw new IllegalArgumentException("编译 shard 与 Runtime 拓扑顺序不一致");
        }
    }

    public record Shard(String id, List<String> roles, String mlog, String mil) {
        public Shard {
            if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("无效的编译 shard id：" + id);
            }
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            if (roles.isEmpty()) throw new IllegalArgumentException("编译 shard 至少需要一个角色：" + id);
            Objects.requireNonNull(mlog, "mlog");
            Objects.requireNonNull(mil, "mil");
        }
    }
}
