package com.arc.mpl.memory;

import com.arc.mpl.hir.HirStringConcat;
import com.arc.mpl.hir.HirText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic descriptors and physical sequences used by the immutable String runtime. */
public record StringRuntimeLayout(
    List<Entry> entries,
    Map<String, Entry> literals,
    Map<Integer, Entry> concatenations,
    Map<Integer, Entry> snapshots,
    Map<Integer, Entry> callResults,
    Map<PhysicalMemoryLayout.StorageKey, Entry> variables,
    Map<String, Entry> functionResults,
    Map<ObjectFieldKey, Entry> objectFields,
    Map<AggregateElementKey, Entry> aggregateElements,
    PhysicalMemoryLayout.Allocation bases,
    PhysicalMemoryLayout.Allocation lengths,
    int slots
) {
    private static final StringRuntimeLayout EMPTY = new StringRuntimeLayout(
        List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null, null, 0);

    public StringRuntimeLayout {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        literals = immutable(literals);
        concatenations = immutable(concatenations);
        snapshots = immutable(snapshots);
        callResults = immutable(callResults);
        variables = immutable(variables);
        functionResults = immutable(functionResults);
        objectFields = immutable(objectFields);
        aggregateElements = immutable(aggregateElements);
        if (!entries.isEmpty() && (bases == null || lengths == null
            || bases.size() != entries.size() || lengths.size() != entries.size())) {
            throw new IllegalArgumentException("String runtime 描述符表大小无效");
        }
        int dataSlots = entries.stream().mapToInt(entry -> entry.allocation().size()).sum();
        int metadataSlots = entries.isEmpty() ? 0 : Math.addExact(bases.size(), lengths.size());
        if (slots < 0 || dataSlots + metadataSlots != slots) {
            throw new IllegalArgumentException("String runtime 槽位统计无效");
        }
        if (entries.stream().map(Entry::handle).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("String runtime handle 必须唯一");
        }
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).handle() != index + 1) {
                throw new IllegalArgumentException("String runtime handle 必须连续且从 1 开始");
            }
        }
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, "source")));
    }

    public static StringRuntimeLayout empty() { return EMPTY; }

    public static PhysicalMemoryLayout.StorageKey basesKey() {
        return new PhysicalMemoryLayout.StorageKey("@string", "descriptorBases");
    }

    public static PhysicalMemoryLayout.StorageKey lengthsKey() {
        return new PhysicalMemoryLayout.StorageKey("@string", "descriptorLengths");
    }

    public boolean enabled() { return !entries.isEmpty(); }

    public Entry literal(HirText text) {
        Entry entry = literals.get(text.value());
        if (entry == null) throw new IllegalArgumentException("String 字面量缺少 runtime 描述符");
        return entry;
    }

    public Entry concatenation(HirStringConcat concat) {
        Entry entry = concatenations.get(concat.allocationId());
        if (entry == null) throw new IllegalArgumentException("String 拼接缺少 runtime 描述符：" + concat.allocationId());
        return entry;
    }

    public Entry callResult(com.arc.mpl.hir.HirFunctionCall call) {
        Entry entry = callResults.get(call.stringResultAllocationId());
        if (entry == null) {
            throw new IllegalArgumentException("String 函数调用缺少独立结果描述符：" + call.function());
        }
        return entry;
    }

    public Entry snapshot(com.arc.mpl.hir.HirStringSnapshot snapshot) {
        Entry entry = snapshots.get(snapshot.allocationId());
        if (entry == null) throw new IllegalArgumentException("String 实参缺少快照描述符");
        return entry;
    }

    public Optional<Entry> variable(String function, String name) {
        Entry local = variables.get(new PhysicalMemoryLayout.StorageKey(function, name));
        if (local != null) return Optional.of(local);
        return Optional.ofNullable(variables.get(new PhysicalMemoryLayout.StorageKey(null, name)));
    }

    public Optional<Entry> functionResult(String function) {
        return Optional.ofNullable(functionResults.get(function));
    }

    public Optional<Entry> objectField(int allocationId, String field, Integer element) {
        return Optional.ofNullable(objectFields.get(new ObjectFieldKey(allocationId, field, element)));
    }

    public Optional<Entry> aggregateElement(String function, String variable, int element) {
        Entry local = aggregateElements.get(new AggregateElementKey(function, variable, element));
        if (local != null) return Optional.of(local);
        return Optional.ofNullable(aggregateElements.get(new AggregateElementKey(null, variable, element)));
    }

    public record ObjectFieldKey(int allocationId, String field, Integer element) {
        public ObjectFieldKey {
            if (allocationId < 1) throw new IllegalArgumentException("对象 String 字段 allocationId 必须为正数");
            Objects.requireNonNull(field, "field");
            if (element != null && element < 0) throw new IllegalArgumentException("对象 String 元组下标不能为负数");
        }
    }

    public record AggregateElementKey(String function, String variable, int element) {
        public AggregateElementKey {
            Objects.requireNonNull(variable, "variable");
            if (element < 0) throw new IllegalArgumentException("String 聚合下标不能为负数");
        }
    }

    /** One immutable logical String value backed by an owned fixed-capacity sequence. */
    public record Entry(int handle, int capacity, int fixedLength, String literal,
                        PhysicalMemoryLayout.Allocation allocation) {
        public Entry {
            if (handle < 1 || capacity < 0 || fixedLength < -1 || fixedLength > capacity) {
                throw new IllegalArgumentException("String runtime 描述符范围无效");
            }
            if ((literal == null) != (fixedLength < 0)) {
                throw new IllegalArgumentException("只有字面量 String 才能使用固定长度");
            }
            if (literal != null && literal.length() != fixedLength) {
                throw new IllegalArgumentException("String 字面量长度与描述符不一致");
            }
            Objects.requireNonNull(allocation, "allocation");
            if (allocation.size() != Math.max(1, capacity)) {
                throw new IllegalArgumentException("String 物理分配与容量不一致");
            }
        }

        public boolean isLiteral() { return literal != null; }
    }
}
