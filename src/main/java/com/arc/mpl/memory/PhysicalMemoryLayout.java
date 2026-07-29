package com.arc.mpl.memory;

import com.arc.mpl.project.RuntimePreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable compiler-owned placement of runtime-indexed values in physical Memory blocks. */
public record PhysicalMemoryLayout(
    List<Segment> segments,
    Map<StorageKey, Allocation> allocations,
    int physicalSlots
) {
    private static final PhysicalMemoryLayout EMPTY = new PhysicalMemoryLayout(List.of(), Map.of(), 0);

    public PhysicalMemoryLayout {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        allocations = Map.copyOf(Objects.requireNonNull(allocations, "allocations"));
        if (physicalSlots < 0) throw new IllegalArgumentException("physicalSlots must be non-negative");
        if (segments.stream().map(Segment::alias).distinct().count() != segments.size()) {
            throw new IllegalArgumentException("physical Memory aliases must be unique");
        }
        int allocatedSlots = allocations.values().stream().mapToInt(Allocation::size).sum();
        if (allocatedSlots != physicalSlots) {
            throw new IllegalArgumentException("physicalSlots must equal the allocated slot count");
        }
        Set<String> occupied = new HashSet<>();
        for (Map.Entry<StorageKey, Allocation> entry : allocations.entrySet()) {
            Allocation allocation = entry.getValue();
            if (!entry.getKey().equals(allocation.key())) {
                throw new IllegalArgumentException("allocation map key does not match allocation key");
            }
            for (Slice slice : allocation.slices()) {
                if (slice.segmentIndex() >= segments.size()) {
                    throw new IllegalArgumentException("allocation references a missing physical Memory segment");
                }
                Segment segment = segments.get(slice.segmentIndex());
                if (slice.offset() + slice.length() > segment.capacity()) {
                    throw new IllegalArgumentException("allocation exceeds its physical Memory segment");
                }
                if (slice.offset() + slice.length() > segment.usedSlots()) {
                    throw new IllegalArgumentException("allocation exceeds the used slots of its physical Memory segment");
                }
                for (int offset = slice.offset(); offset < slice.offset() + slice.length(); offset++) {
                    if (!occupied.add(slice.segmentIndex() + ":" + offset)) {
                        throw new IllegalArgumentException("physical Memory allocations overlap");
                    }
                }
            }
        }
    }

    public static PhysicalMemoryLayout empty() {
        return EMPTY;
    }

    public Optional<Allocation> allocation(String function, String variable) {
        StorageKey local = new StorageKey(function, variable);
        Allocation allocation = allocations.get(local);
        if (allocation == null && function != null) allocation = allocations.get(new StorageKey(null, variable));
        return Optional.ofNullable(allocation);
    }

    public long memoryCells() {
        return segments.stream().filter(segment -> segment.kind() == RuntimePreferences.MemoryKind.CELL).count();
    }

    public long memoryBanks() {
        return segments.stream().filter(segment -> segment.kind() == RuntimePreferences.MemoryKind.BANK).count();
    }

    public record StorageKey(String function, String variable) {
        public StorageKey {
            Objects.requireNonNull(variable, "variable");
        }
    }

    public record Segment(String alias, RuntimePreferences.MemoryKind kind, int capacity, int usedSlots) {
        public Segment {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(kind, "kind");
            if (capacity < 1 || usedSlots < 0 || usedSlots > capacity) {
                throw new IllegalArgumentException("invalid physical Memory segment size");
            }
        }
    }

    public record Allocation(StorageKey key, int size, List<Slice> slices) {
        public Allocation {
            Objects.requireNonNull(key, "key");
            if (size < 1) throw new IllegalArgumentException("allocation size must be positive");
            slices = List.copyOf(Objects.requireNonNull(slices, "slices"));
            if (slices.stream().mapToInt(Slice::length).sum() != size) {
                throw new IllegalArgumentException("allocation slices do not cover its size");
            }
            int nextLogicalStart = 0;
            for (Slice slice : slices) {
                if (slice.logicalStart() != nextLogicalStart) {
                    throw new IllegalArgumentException("allocation slices must be logically contiguous");
                }
                nextLogicalStart += slice.length();
            }
        }
    }

    public record Slice(int segmentIndex, int offset, int logicalStart, int length) {
        public Slice {
            if (segmentIndex < 0 || offset < 0 || logicalStart < 0 || length < 1) {
                throw new IllegalArgumentException("invalid physical Memory slice");
            }
        }

        public boolean contains(int index) {
            return index >= logicalStart && index < logicalStart + length;
        }
    }
}
