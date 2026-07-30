package com.arc.mpl.memory;

import java.util.Objects;

/** One fixed-width, single-producer/single-consumer mailbox in shared physical Memory. */
public record SharedMailboxLayout(String id, String producer, String consumer, int payloadSlots,
                                  PhysicalMemoryLayout.Allocation allocation) {
    public static final int VERSION_INDEX = 0;
    public static final int ACKNOWLEDGED_VERSION_INDEX = 1;
    public static final int KIND_INDEX = 2;
    public static final int PAYLOAD_START = 3;

    public SharedMailboxLayout {
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("无效的共享邮箱 id：" + id);
        }
        if (producer == null || producer.isBlank() || consumer == null || consumer.isBlank()
            || producer.equals(consumer)) {
            throw new IllegalArgumentException("共享邮箱必须有两个不同的生产者和消费者");
        }
        if (payloadSlots < 0) throw new IllegalArgumentException("共享邮箱 payload 槽数不得为负数");
        Objects.requireNonNull(allocation, "allocation");
        if (!allocation.key().equals(storageKey(id))) {
            throw new IllegalArgumentException("共享邮箱 allocation key 无效：" + id);
        }
        if (allocation.size() != requiredSlots(payloadSlots)) {
            throw new IllegalArgumentException("共享邮箱 allocation 大小无效：" + id);
        }
    }

    public static PhysicalMemoryLayout.StorageKey storageKey(String id) {
        return new PhysicalMemoryLayout.StorageKey("@runtime:mailbox:" + id, "storage");
    }

    public static int requiredSlots(int payloadSlots) {
        if (payloadSlots < 0) throw new IllegalArgumentException("共享邮箱 payload 槽数不得为负数");
        return Math.addExact(PAYLOAD_START, payloadSlots);
    }

    public int payloadIndex(int index) {
        if (index < 0 || index >= payloadSlots) throw new IllegalArgumentException("共享邮箱 payload 下标越界：" + index);
        return PAYLOAD_START + index;
    }

    public int slots() {
        return allocation.size();
    }
}
