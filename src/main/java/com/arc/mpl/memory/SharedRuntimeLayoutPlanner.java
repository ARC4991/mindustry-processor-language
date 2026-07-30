package com.arc.mpl.memory;

import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimePreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/** Allocates and fingerprints the versioned multi-shard startup header. */
public final class SharedRuntimeLayoutPlanner {
    public Result plan(PhysicalMemoryLayout base, String mainShard, List<String> workers, String epochSeed,
                       TargetProfile profile, RuntimePreferences preferences) {
        Objects.requireNonNull(epochSeed, "epochSeed");
        PhysicalMemoryLayout extended = new PhysicalMemoryLayoutExtender().allocate(base,
            SharedRuntimeLayout.storageKey(), SharedRuntimeLayout.requiredSlots(workers.size()), profile, preferences);
        PhysicalMemoryLayout.Allocation header = extended.allocations().get(SharedRuntimeLayout.storageKey());
        String topology = mainShard + "\n" + String.join("\n", workers) + "\n"
            + extended.segments().stream().map(segment -> segment.alias() + ":" + segment.kind() + ":"
                + segment.capacity() + ":" + segment.usedSlots())
                .collect(java.util.stream.Collectors.joining("\n"));
        int fingerprint = positiveDigest(topology);
        int epoch = positiveDigest(topology + "\n" + epochSeed);
        return new Result(extended, new SharedRuntimeLayout(header, mainShard, workers, fingerprint, epoch));
    }

    private int positiveDigest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            int result = ((digest[0] & 0x7f) << 24) | ((digest[1] & 0xff) << 16)
                | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
            return result == 0 ? 1 : result;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    public record Result(PhysicalMemoryLayout physicalMemoryLayout, SharedRuntimeLayout sharedRuntime) {
        public Result {
            Objects.requireNonNull(physicalMemoryLayout, "physicalMemoryLayout");
            Objects.requireNonNull(sharedRuntime, "sharedRuntime");
        }
    }
}
