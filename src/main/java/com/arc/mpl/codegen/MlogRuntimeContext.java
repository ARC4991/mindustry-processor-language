package com.arc.mpl.codegen;

import com.arc.mpl.memory.SharedRuntimeLayout;

import java.util.Objects;
import java.util.Optional;

/** Identifies one generated processor program and its compiler-private shared startup protocol. */
public record MlogRuntimeContext(String shardId, Optional<SharedRuntimeLayout> sharedRuntime) {
    public MlogRuntimeContext {
        if (shardId == null || !shardId.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("无效的 mlog shard id：" + shardId);
        }
        sharedRuntime = sharedRuntime == null ? Optional.empty() : sharedRuntime;
        sharedRuntime.ifPresent(layout -> {
            if (!layout.mainShard().equals(shardId) && !layout.workers().contains(shardId)) {
                throw new IllegalArgumentException("shard 不属于共享 Runtime：" + shardId);
            }
        });
    }

    public static MlogRuntimeContext singleShard() {
        return new MlogRuntimeContext("Main", Optional.empty());
    }

    public static MlogRuntimeContext shared(String shardId, SharedRuntimeLayout layout) {
        return new MlogRuntimeContext(shardId, Optional.of(Objects.requireNonNull(layout, "layout")));
    }

    public boolean main() {
        return sharedRuntime.map(layout -> layout.mainShard().equals(shardId)).orElse(true);
    }

    public boolean worker() {
        return !main();
    }
}
