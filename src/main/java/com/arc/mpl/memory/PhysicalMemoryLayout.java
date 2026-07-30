package com.arc.mpl.memory;

import com.arc.mpl.project.RuntimePreferences;
import com.arc.mpl.hir.MplType;

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
    Map<String, ObjectPool> objectPools,
    int physicalSlots,
    int objectPoolSlots
) {
    private static final PhysicalMemoryLayout EMPTY = new PhysicalMemoryLayout(List.of(), Map.of(), Map.of(), 0, 0);

    public PhysicalMemoryLayout(List<Segment> segments, Map<StorageKey, Allocation> allocations, int physicalSlots) {
        this(segments, allocations, Map.of(), physicalSlots, 0);
    }

    public PhysicalMemoryLayout {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        allocations = java.util.Collections.unmodifiableMap(
            new java.util.LinkedHashMap<>(Objects.requireNonNull(allocations, "allocations")));
        objectPools = java.util.Collections.unmodifiableMap(
            new java.util.LinkedHashMap<>(Objects.requireNonNull(objectPools, "objectPools")));
        if (physicalSlots < 0 || objectPoolSlots < 0 || objectPoolSlots > physicalSlots) {
            throw new IllegalArgumentException("invalid physical/object-pool slot counts");
        }
        if (segments.stream().map(Segment::alias).distinct().count() != segments.size()) {
            throw new IllegalArgumentException("physical Memory aliases must be unique");
        }
        int allocatedSlots = allocations.values().stream().mapToInt(Allocation::size).sum();
        if (allocatedSlots != physicalSlots) {
            throw new IllegalArgumentException("physicalSlots must equal the allocated slot count");
        }
        int pooledSlots = objectPools.values().stream().mapToInt(ObjectPool::slots).sum();
        if (pooledSlots != objectPoolSlots) {
            throw new IllegalArgumentException("objectPoolSlots must equal the object-pool allocation count");
        }
        for (Map.Entry<String, ObjectPool> entry : objectPools.entrySet()) {
            ObjectPool pool = entry.getValue();
            if (!entry.getKey().equals(pool.className())) {
                throw new IllegalArgumentException("object-pool map key does not match its class");
            }
            if (!pool.occupancy().key().equals(objectPoolOccupancyKey(pool.className()))
                || !pool.occupancy().equals(allocations.get(pool.occupancy().key()))) {
                throw new IllegalArgumentException("object-pool occupancy is not part of the physical layout");
            }
            for (Map.Entry<String, PoolField> field : pool.fields().entrySet()) {
                if (!field.getKey().equals(field.getValue().name())
                    || !field.getValue().allocation().key().equals(
                        objectPoolFieldKey(pool.className(), field.getValue().name()))
                    || !field.getValue().allocation().equals(allocations.get(field.getValue().allocation().key()))) {
                    throw new IllegalArgumentException("object-pool field is not part of the physical layout");
                }
            }
        }
        Set<Integer> handles = new HashSet<>();
        for (ObjectPool pool : objectPools.values()) {
            for (int slot = 0; slot < pool.capacity(); slot++) {
                if (!handles.add(pool.handleBase() + slot)) {
                    throw new IllegalArgumentException("object-pool handle ranges overlap");
                }
            }
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

    public Optional<ObjectPool> objectPool(String className) {
        return Optional.ofNullable(objectPools.get(className));
    }

    public static StorageKey objectPoolOccupancyKey(String className) {
        return new StorageKey("@objectPool:" + className, "occupancy");
    }

    public static StorageKey objectPoolFieldKey(String className, String field) {
        return new StorageKey("@objectPool:" + className, field);
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

    /** One class-specific unique-owner pool backed by compiler-allocated physical Memory. */
    public record ObjectPool(String className, int capacity, int handleBase, Allocation occupancy,
                             Map<String, PoolField> fields) {
        public ObjectPool {
            Objects.requireNonNull(className, "className");
            Objects.requireNonNull(occupancy, "occupancy");
            fields = java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(Objects.requireNonNull(fields, "fields")));
            if (capacity < 1 || handleBase < 0 || occupancy.size() != capacity) {
                throw new IllegalArgumentException("invalid object-pool capacity or occupancy layout");
            }
            for (PoolField field : fields.values()) {
                if (field.allocation().size() != Math.multiplyExact(capacity, field.width())) {
                    throw new IllegalArgumentException("object-pool field allocation has the wrong size");
                }
            }
        }

        public int slots() {
            int slots = occupancy.size();
            for (PoolField field : fields.values()) slots = Math.addExact(slots, field.allocation().size());
            return slots;
        }

        public PoolField field(String name) {
            PoolField field = fields.get(name);
            if (field == null) throw new IllegalArgumentException("unknown object-pool field: " + className + "." + name);
            return field;
        }
    }

    public record PoolField(String name, MplType type, int width, Allocation allocation) {
        public PoolField {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(allocation, "allocation");
            if (width < 1) throw new IllegalArgumentException("object-pool field width must be positive");
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
