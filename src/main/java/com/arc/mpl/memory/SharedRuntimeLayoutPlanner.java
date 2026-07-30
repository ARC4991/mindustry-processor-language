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
        return plan(base, mainShard, workers, List.of(), epochSeed, profile, preferences);
    }

    public Result plan(PhysicalMemoryLayout base, String mainShard, List<String> workers,
                       List<MailboxRequirement> mailboxRequirements, String epochSeed,
                       TargetProfile profile, RuntimePreferences preferences) {
        Objects.requireNonNull(epochSeed, "epochSeed");
        mailboxRequirements = List.copyOf(Objects.requireNonNull(mailboxRequirements, "mailboxRequirements"));
        if (mailboxRequirements.stream().map(MailboxRequirement::id).distinct().count() != mailboxRequirements.size()) {
            throw new IllegalArgumentException("共享 Runtime 邮箱需求 id 不能重复");
        }
        PhysicalMemoryLayout extended = new PhysicalMemoryLayoutExtender().allocate(base,
            SharedRuntimeLayout.storageKey(), SharedRuntimeLayout.requiredSlots(workers.size()), profile, preferences);
        PhysicalMemoryLayout.Allocation header = extended.allocations().get(SharedRuntimeLayout.storageKey());
        List<SharedMailboxLayout> mailboxes = new java.util.ArrayList<>();
        PhysicalMemoryLayoutExtender extender = new PhysicalMemoryLayoutExtender();
        for (MailboxRequirement requirement : mailboxRequirements) {
            extended = extender.allocate(extended, SharedMailboxLayout.storageKey(requirement.id()),
                SharedMailboxLayout.requiredSlots(requirement.payloadSlots()), profile, preferences);
            mailboxes.add(new SharedMailboxLayout(requirement.id(), requirement.producer(), requirement.consumer(),
                requirement.payloadSlots(), extended.allocations().get(SharedMailboxLayout.storageKey(requirement.id()))));
        }
        String topology = mainShard + "\n" + String.join("\n", workers) + "\n"
            + mailboxRequirements.stream().map(requirement -> requirement.id() + ":" + requirement.producer() + ":"
                + requirement.consumer() + ":" + requirement.payloadSlots())
                .collect(java.util.stream.Collectors.joining("\n")) + "\n"
            + extended.segments().stream().map(segment -> segment.alias() + ":" + segment.kind() + ":"
                + segment.capacity() + ":" + segment.usedSlots())
                .collect(java.util.stream.Collectors.joining("\n"));
        int fingerprint = positiveDigest(topology);
        int epoch = positiveDigest(topology + "\n" + epochSeed);
        return new Result(extended, new SharedRuntimeLayout(header, mainShard, workers, fingerprint, epoch, mailboxes));
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

    public record MailboxRequirement(String id, String producer, String consumer, int payloadSlots) {
        public MailboxRequirement {
            if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("无效的共享邮箱需求 id：" + id);
            }
            if (producer == null || producer.isBlank() || consumer == null || consumer.isBlank()
                || producer.equals(consumer)) {
                throw new IllegalArgumentException("共享邮箱需求必须有两个不同端点：" + id);
            }
            if (payloadSlots < 0) throw new IllegalArgumentException("共享邮箱 payload 槽数不得为负数");
        }
    }
}
