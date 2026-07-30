package com.arc.mpl.memory;

import com.arc.mpl.hir.CollectionType;
import com.arc.mpl.hir.HirArrayLiteral;
import com.arc.mpl.hir.HirConstant;
import com.arc.mpl.hir.HirClass;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirExpression;
import com.arc.mpl.hir.HirFunction;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirNewObject;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.hir.HirReturn;
import com.arc.mpl.hir.HirStatement;
import com.arc.mpl.hir.HirVariable;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.MplType;
import com.arc.mpl.hir.ObjectType;
import com.arc.mpl.hir.TupleType;
import com.arc.mpl.hir.ValueType;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimePreferences;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalMemoryPlannerTest {
    private static final CollectionType INT_ARRAY = new CollectionType(CollectionType.Kind.ARRAY, ValueType.INT);
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
    private final PhysicalMemoryPlanner planner = new PhysicalMemoryPlanner();

    @Test
    void placesOneRuntimeIndexedArrayInOneBank() {
        PhysicalMemoryLayout layout = planner.plan(program(array("values", 3), dynamicRead("values")), profile,
            RuntimePreferences.defaults());

        assertEquals(3, layout.physicalSlots());
        assertEquals(1, layout.memoryBanks());
        assertEquals(0, layout.memoryCells());
        assertEquals(new PhysicalMemoryLayout.Segment("__mpl_mem0", RuntimePreferences.MemoryKind.BANK, 512, 3),
            layout.segments().get(0));
        assertEquals(List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 3)), allocation(layout, "values").slices());
    }

    @Test
    void splitsCellOnlyStorageAtTheProfileCapacity() {
        PhysicalMemoryLayout layout = planner.plan(program(array("values", 100), dynamicRead("values")), profile,
            preferences(Map.of(RuntimePreferences.MemoryKind.CELL, 2)));

        assertEquals(2, layout.memoryCells());
        assertEquals(List.of(
            new PhysicalMemoryLayout.Slice(0, 0, 0, 64),
            new PhysicalMemoryLayout.Slice(1, 0, 64, 36)
        ), allocation(layout, "values").slices());
        assertEquals(36, layout.segments().get(1).usedSlots());
    }

    @Test
    void preservesLogicalOffsetsWhenAnAllocationCrossesAUsedSegment() {
        PhysicalMemoryLayout layout = planner.plan(program(
            array("first", 40), array("second", 40), dynamicRead("first"), dynamicRead("second")), profile,
            preferences(Map.of(RuntimePreferences.MemoryKind.CELL, 2)));

        assertEquals(List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 40)), allocation(layout, "first").slices());
        assertEquals(List.of(
            new PhysicalMemoryLayout.Slice(0, 40, 0, 24),
            new PhysicalMemoryLayout.Slice(1, 0, 24, 16)
        ), allocation(layout, "second").slices());
    }

    @Test
    void continuesWithCellsWhenTheAllowedBanksAreInsufficient() {
        PhysicalMemoryLayout layout = planner.plan(program(array("values", 600), dynamicRead("values")), profile,
            preferences(Map.of(RuntimePreferences.MemoryKind.BANK, 1, RuntimePreferences.MemoryKind.CELL, 2)));

        assertEquals(List.of(
            RuntimePreferences.MemoryKind.BANK,
            RuntimePreferences.MemoryKind.CELL,
            RuntimePreferences.MemoryKind.CELL
        ), layout.segments().stream().map(PhysicalMemoryLayout.Segment::kind).toList());
        assertEquals(List.of(
            new PhysicalMemoryLayout.Slice(0, 0, 0, 512),
            new PhysicalMemoryLayout.Slice(1, 0, 512, 64),
            new PhysicalMemoryLayout.Slice(2, 0, 576, 24)
        ), allocation(layout, "values").slices());
    }

    @Test
    void rejectsAPlanBeyondTheAllowedPhysicalMemory() {
        RuntimePreferences preferences = preferences(Map.of(RuntimePreferences.MemoryKind.CELL, 1));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> planner.plan(program(array("values", 65), dynamicRead("values")), profile, preferences));

        assertTrue(error.getMessage().contains("65 个物理槽"));
    }

    @Test
    void splitsObjectPoolFieldsAcrossPhysicalMemorySegments() {
        TupleType wideTuple = new TupleType(java.util.stream.IntStream.range(0, 64)
            .mapToObj(ignored -> (MplType) ValueType.INT).toList());
        ObjectType objectType = new ObjectType("Wide", false);
        HirClass type = new HirClass("Wide", false,
            List.of(new HirClass.Field("values", wideTuple, true)), List.of());
        HirNewObject allocation = new HirNewObject(1, "Wide", "__mpl_class_Wide_Wide", List.of(), objectType,
            HirNewObject.AllocationKind.POOLED);
        HirFunction factory = new HirFunction("create", List.of(), objectType,
            List.of(new HirReturn(Optional.of(allocation))));
        HirVariableDeclaration owner = new HirVariableDeclaration("owned", objectType, false,
            new HirFunctionCall("create", List.of(), objectType), true);

        PhysicalMemoryLayout layout = planner.plan(new HirProgram(List.of(type), List.of(factory), List.of(owner)), profile,
            preferences(Map.of(RuntimePreferences.MemoryKind.CELL, 2)));

        PhysicalMemoryLayout.ObjectPool pool = layout.objectPool("Wide").orElseThrow();
        assertEquals(65, layout.objectPoolSlots());
        assertEquals(List.of(
            new PhysicalMemoryLayout.Slice(0, 1, 0, 63),
            new PhysicalMemoryLayout.Slice(1, 0, 63, 1)
        ), pool.field("values").allocation().slices());
    }

    private HirVariableDeclaration array(String name, int size) {
        List<HirExpression> elements = new ArrayList<>();
        for (int index = 0; index < size; index++) elements.add(new HirConstant(Integer.toString(index), ValueType.INT));
        return new HirVariableDeclaration(name, INT_ARRAY, true, new HirArrayLiteral(elements, INT_ARRAY));
    }

    private HirPrintStatement dynamicRead(String name) {
        return new HirPrintStatement("message1", List.of(new HirDynamicIndexAccess(
            new HirVariable(name, INT_ARRAY), new HirVariable("index", ValueType.INT), ValueType.INT)));
    }

    private HirProgram program(HirStatement... statements) {
        return new HirProgram(List.of(), List.of(statements));
    }

    private RuntimePreferences preferences(Map<RuntimePreferences.MemoryKind, Integer> memory) {
        return new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            RuntimePreferences.defaults().processors(), memory);
    }

    private PhysicalMemoryLayout.Allocation allocation(PhysicalMemoryLayout layout, String variable) {
        return layout.allocation(null, variable).orElseThrow();
    }
}
