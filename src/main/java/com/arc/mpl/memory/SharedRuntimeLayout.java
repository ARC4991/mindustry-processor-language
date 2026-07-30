package com.arc.mpl.memory;

import java.util.List;
import java.util.Objects;

/** Versioned shared-Memory header and per-Worker heartbeat slots. */
public record SharedRuntimeLayout(PhysicalMemoryLayout.Allocation header, String mainShard,
                                  List<String> workers, int fingerprint, int epoch,
                                  List<SharedMailboxLayout> mailboxes) {
    public static final int MAGIC = 0x4d504c;
    public static final int ABI_VERSION = 2;
    public static final int MAGIC_INDEX = 0;
    public static final int ABI_INDEX = 1;
    public static final int FINGERPRINT_INDEX = 2;
    public static final int EPOCH_INDEX = 3;
    public static final int READY_INDEX = 4;
    public static final int ACKNOWLEDGEMENT_START = 5;

    public SharedRuntimeLayout {
        Objects.requireNonNull(header, "header");
        if (!header.key().equals(storageKey())) throw new IllegalArgumentException("共享 Runtime header key 无效");
        if (mainShard == null || mainShard.isBlank()) throw new IllegalArgumentException("共享 Runtime 缺少 Main shard");
        workers = List.copyOf(Objects.requireNonNull(workers, "workers"));
        if (workers.isEmpty() || workers.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("多处理器共享 Runtime 至少需要一个 Worker");
        }
        if (workers.contains(mainShard) || workers.stream().distinct().count() != workers.size()) {
            throw new IllegalArgumentException("共享 Runtime shard 身份重复");
        }
        if (header.size() != requiredSlots(workers.size())) {
            throw new IllegalArgumentException("共享 Runtime header 槽数与 Worker 数量不匹配");
        }
        if (fingerprint <= 0 || epoch <= 0) throw new IllegalArgumentException("共享 Runtime 指纹和 epoch 必须为正数");
        mailboxes = List.copyOf(Objects.requireNonNull(mailboxes, "mailboxes"));
        if (mailboxes.stream().map(SharedMailboxLayout::id).distinct().count() != mailboxes.size()) {
            throw new IllegalArgumentException("共享 Runtime 邮箱 id 不能重复");
        }
        List<String> shards = new java.util.ArrayList<>();
        shards.add(mainShard);
        shards.addAll(workers);
        for (SharedMailboxLayout mailbox : mailboxes) {
            if (!shards.contains(mailbox.producer()) || !shards.contains(mailbox.consumer())) {
                throw new IllegalArgumentException("共享邮箱端点不属于 Runtime 拓扑：" + mailbox.id());
            }
        }
    }

    public SharedRuntimeLayout(PhysicalMemoryLayout.Allocation header, String mainShard,
                               List<String> workers, int fingerprint, int epoch) {
        this(header, mainShard, workers, fingerprint, epoch, List.of());
    }

    public static PhysicalMemoryLayout.StorageKey storageKey() {
        return new PhysicalMemoryLayout.StorageKey("@runtime:shared", "header");
    }

    public static int requiredSlots(int workers) {
        if (workers < 1) throw new IllegalArgumentException("Worker 数量必须为正数");
        return Math.addExact(ACKNOWLEDGEMENT_START, Math.multiplyExact(workers, 2));
    }

    public int acknowledgementIndex(String worker) {
        int index = workers.indexOf(worker);
        if (index < 0) throw new IllegalArgumentException("未知共享 Runtime Worker：" + worker);
        return ACKNOWLEDGEMENT_START + index;
    }

    public int heartbeatIndex(String worker) {
        int index = workers.indexOf(worker);
        if (index < 0) throw new IllegalArgumentException("未知共享 Runtime Worker：" + worker);
        return ACKNOWLEDGEMENT_START + workers.size() + index;
    }

    public int slots() {
        return Math.addExact(header.size(), mailboxes.stream().mapToInt(SharedMailboxLayout::slots).sum());
    }

    public int headerSlots() {
        return header.size();
    }

    public SharedMailboxLayout mailbox(String id) {
        return mailboxes.stream().filter(mailbox -> mailbox.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知共享 Runtime 邮箱：" + id));
    }
}
