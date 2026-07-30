package com.arc.mpl.memory;

import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimePreferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalMemoryLayoutExtenderTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
    private final PhysicalMemoryLayoutExtender extender = new PhysicalMemoryLayoutExtender();

    @Test
    void appendsIntoUnusedCapacityWithoutMovingExistingAllocations() {
        PhysicalMemoryLayout.StorageKey existingKey = new PhysicalMemoryLayout.StorageKey(null, "values");
        PhysicalMemoryLayout.Allocation existing = new PhysicalMemoryLayout.Allocation(existingKey, 500,
            List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 500)));
        PhysicalMemoryLayout base = new PhysicalMemoryLayout(List.of(
            new PhysicalMemoryLayout.Segment("bank1", RuntimePreferences.MemoryKind.BANK, 512, 500)
        ), Map.of(existingKey, existing), 500);
        PhysicalMemoryLayout.StorageKey runtimeKey = new PhysicalMemoryLayout.StorageKey("@runtime:test", "header");

        PhysicalMemoryLayout result = extender.allocate(base, runtimeKey, 12, profile,
            preferences(Map.of(RuntimePreferences.MemoryKind.BANK, 1)));

        assertEquals(1, result.segments().size());
        assertEquals(512, result.segments().get(0).usedSlots());
        assertSame(existing, result.allocations().get(existingKey));
        assertEquals(List.of(new PhysicalMemoryLayout.Slice(0, 500, 0, 12)),
            result.allocations().get(runtimeKey).slices());
        assertEquals(512, result.physicalSlots());
    }

    @Test
    void createsAdditionalAllowedSegmentsAndKeepsLogicalSlicesContiguous() {
        PhysicalMemoryLayout.StorageKey runtimeKey = new PhysicalMemoryLayout.StorageKey("@runtime:test", "header");

        PhysicalMemoryLayout result = extender.allocate(PhysicalMemoryLayout.empty(), runtimeKey, 70, profile,
            preferences(Map.of(RuntimePreferences.MemoryKind.CELL, 2)));

        assertEquals(List.of(
            new PhysicalMemoryLayout.Segment("cell1", RuntimePreferences.MemoryKind.CELL, 64, 64),
            new PhysicalMemoryLayout.Segment("cell2", RuntimePreferences.MemoryKind.CELL, 64, 6)
        ), result.segments());
        assertEquals(List.of(
            new PhysicalMemoryLayout.Slice(0, 0, 0, 64),
            new PhysicalMemoryLayout.Slice(1, 0, 64, 6)
        ), result.allocations().get(runtimeKey).slices());
    }

    @Test
    void rejectsAnExtensionBeyondTheConfiguredMemoryBlocks() {
        PhysicalMemoryLayout.StorageKey runtimeKey = new PhysicalMemoryLayout.StorageKey("@runtime:test", "header");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> extender.allocate(PhysicalMemoryLayout.empty(), runtimeKey, 65, profile,
                preferences(Map.of(RuntimePreferences.MemoryKind.CELL, 1))));

        assertTrue(error.getMessage().contains("无法追加 65 个协议槽"));
    }

    private RuntimePreferences preferences(Map<RuntimePreferences.MemoryKind, Integer> memory) {
        return new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            RuntimePreferences.defaults().processors(), memory);
    }
}
