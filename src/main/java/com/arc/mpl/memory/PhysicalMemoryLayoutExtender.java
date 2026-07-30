package com.arc.mpl.memory;

import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimePreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Appends compiler Runtime allocations without moving existing user/runtime storage. */
public final class PhysicalMemoryLayoutExtender {
    public PhysicalMemoryLayout allocate(PhysicalMemoryLayout base, PhysicalMemoryLayout.StorageKey key, int slots,
                                         TargetProfile profile, RuntimePreferences preferences) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(preferences, "preferences");
        if (slots < 1) throw new IllegalArgumentException("追加的物理 Memory 槽数必须为正数");
        if (base.allocations().containsKey(key)) throw new IllegalArgumentException("物理 Memory key 已分配：" + key);

        List<PhysicalMemoryLayout.Segment> segments = new ArrayList<>(base.segments());
        List<PhysicalMemoryLayout.Slice> slices = new ArrayList<>();
        int remaining = slots;
        int logicalStart = 0;
        for (int index = 0; index < segments.size() && remaining > 0; index++) {
            PhysicalMemoryLayout.Segment segment = segments.get(index);
            int available = segment.capacity() - segment.usedSlots();
            if (available == 0) continue;
            int length = Math.min(available, remaining);
            slices.add(new PhysicalMemoryLayout.Slice(index, segment.usedSlots(), logicalStart, length));
            segments.set(index, new PhysicalMemoryLayout.Segment(segment.alias(), segment.kind(), segment.capacity(),
                segment.usedSlots() + length));
            logicalStart += length;
            remaining -= length;
        }

        if (remaining > 0) {
            Map<RuntimePreferences.MemoryKind, Integer> existing = new java.util.EnumMap<>(RuntimePreferences.MemoryKind.class);
            for (PhysicalMemoryLayout.Segment segment : segments) existing.merge(segment.kind(), 1, Integer::sum);
            for (RuntimePreferences.MemoryKind kind : List.of(
                RuntimePreferences.MemoryKind.BANK, RuntimePreferences.MemoryKind.CELL)) {
                int allowed = preferences.memory().getOrDefault(kind, 0);
                int availableBlocks = Math.max(0, allowed - existing.getOrDefault(kind, 0));
                int capacity = capacity(kind, profile);
                while (remaining > 0 && availableBlocks-- > 0) {
                    int index = segments.size();
                    int length = Math.min(remaining, capacity);
                    String alias = (kind == RuntimePreferences.MemoryKind.CELL ? "cell" : "bank")
                        + "__mpl_mem" + index;
                    segments.add(new PhysicalMemoryLayout.Segment(alias, kind, capacity, length));
                    slices.add(new PhysicalMemoryLayout.Slice(index, 0, logicalStart, length));
                    logicalStart += length;
                    remaining -= length;
                }
                if (remaining == 0) break;
            }
        }
        if (remaining > 0) {
            throw new IllegalArgumentException("运行时 Memory 约束无法追加 " + slots + " 个协议槽");
        }

        PhysicalMemoryLayout.Allocation allocation = new PhysicalMemoryLayout.Allocation(key, slots, slices);
        Map<PhysicalMemoryLayout.StorageKey, PhysicalMemoryLayout.Allocation> allocations =
            new LinkedHashMap<>(base.allocations());
        allocations.put(key, allocation);
        return new PhysicalMemoryLayout(segments, allocations, base.objectPools(), base.stringRuntime(),
            Math.addExact(base.physicalSlots(), slots), base.objectPoolSlots());
    }

    private int capacity(RuntimePreferences.MemoryKind kind, TargetProfile profile) {
        return kind == RuntimePreferences.MemoryKind.CELL
            ? profile.memoryCellCapacity() : profile.memoryBankCapacity();
    }
}
