package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.profile.KnownProfiles;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintLayoutTest {
    @Test
    void keepsAStringRuntimeProcessorAndBanksInOneTightStrip() {
        PhysicalMemoryLayout memory = new PhysicalMemoryLayout(List.of(
            segment("bank1", RuntimePreferences.MemoryKind.BANK, 512),
            segment("bank2", RuntimePreferences.MemoryKind.BANK, 512),
            segment("bank3", RuntimePreferences.MemoryKind.BANK, 512),
            segment("bank4", RuntimePreferences.MemoryKind.BANK, 512),
            segment("bank5", RuntimePreferences.MemoryKind.BANK, 512)
        ), Map.of(), 0);
        RuntimePlan plan = new RuntimePlan(TargetProfile.ProcessorKind.MICRO, 1, 0, 3, 0, memory);

        BlueprintLayout layout = BlueprintLayout.singleShard(plan);

        assertEquals(11, layout.width());
        assertEquals(2, layout.height());
        assertEquals(new Position(0, 0), new Position(layout.main().x(), layout.main().y()));
        assertEquals(List.of(
            new Position(1, 0), new Position(3, 0), new Position(5, 0),
            new Position(7, 0), new Position(9, 0)
        ), layout.memories().stream().map(value -> new Position(value.x(), value.y())).toList());
    }

    @Test
    void packsProcessorBanksAndCellsWithoutDecorativeGaps() {
        PhysicalMemoryLayout memory = new PhysicalMemoryLayout(List.of(
            segment("bank1", RuntimePreferences.MemoryKind.BANK, 512),
            segment("bank2", RuntimePreferences.MemoryKind.BANK, 512),
            segment("cell1", RuntimePreferences.MemoryKind.CELL, 64),
            segment("cell2", RuntimePreferences.MemoryKind.CELL, 64)
        ), Map.of(), 0);
        RuntimePlan plan = new RuntimePlan(TargetProfile.ProcessorKind.HYPER, 1, 0, 3, 0, memory);

        BlueprintLayout layout = BlueprintLayout.singleShard(plan);

        assertEquals(5, layout.width());
        assertEquals(4, layout.height());
        assertEquals(1, layout.main().x());
        assertEquals(1, layout.main().y());
        assertEquals(List.of(
            new Position(3, 0), new Position(3, 2), new Position(0, 3), new Position(1, 3)
        ), layout.memories().stream().map(value -> new Position(value.x(), value.y())).toList());
    }

    @Test
    void packsMultipleShardsBesideOneSharedBank() {
        PhysicalMemoryLayout memory = new PhysicalMemoryLayout(List.of(
            segment("bank1", RuntimePreferences.MemoryKind.BANK, 512)
        ), Map.of(), 0);
        RuntimeTopologyPlan plan = new RuntimeTopologyPlan(List.of(
            new ShardPlan("Main", List.of("main"), TargetProfile.ProcessorKind.MICRO, 2, 0, 4, 0),
            new ShardPlan("Worker-0", List.of("worker"), TargetProfile.ProcessorKind.MICRO, 2, 0, 4, 1)
        ), memory);

        BlueprintLayout layout = BlueprintLayout.topology(plan);

        assertEquals(2, layout.width());
        assertEquals(3, layout.height());
        assertEquals(List.of(new Position(0, 0), new Position(1, 0)),
            layout.shards().stream().map(value -> new Position(value.x(), value.y())).toList());
        assertEquals(List.of(new Position(0, 1)),
            layout.memories().stream().map(value -> new Position(value.x(), value.y())).toList());
    }

    @Test
    void rejectsAConfiguredMemoryLinkOutsideTheProcessorRange() {
        PhysicalMemoryLayout memory = new PhysicalMemoryLayout(List.of(
            segment("bank1", RuntimePreferences.MemoryKind.BANK, 512)
        ), Map.of(), 0);
        RuntimeTopologyPlan plan = new RuntimeTopologyPlan(List.of(
            new ShardPlan("Main", List.of("main"), TargetProfile.ProcessorKind.MICRO, 1, 0, 2, 0)
        ), memory);
        BlueprintLayout layout = new BlueprintLayout(22, 2,
            List.of(new BlueprintLayout.ShardPlacement("Main", "micro", List.of("main"), 0, 0)),
            List.of(new BlueprintLayout.MemoryPlacement(memory.segments().get(0), 20, 0)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> layout.validateInternalLinks(plan, KnownProfiles.find("v146").orElseThrow()));

        assertTrue(error.getMessage().contains("10 格连接半径"));
    }

    private PhysicalMemoryLayout.Segment segment(String alias, RuntimePreferences.MemoryKind kind, int capacity) {
        return new PhysicalMemoryLayout.Segment(alias, kind, capacity, 0);
    }

    private record Position(int x, int y) { }
}
