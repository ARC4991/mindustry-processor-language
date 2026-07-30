package com.arc.mpl.memory;

import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.RuntimePreferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SharedRuntimeLayoutPlannerTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
    private final SharedRuntimeLayoutPlanner planner = new SharedRuntimeLayoutPlanner();

    @Test
    void allocatesFiveHeaderFieldsAndOneHeartbeatPerWorker() {
        SharedRuntimeLayoutPlanner.Result result = planner.plan(PhysicalMemoryLayout.empty(), "Main",
            List.of("Worker-0", "Worker-1"), "code-a", profile, RuntimePreferences.defaults());

        SharedRuntimeLayout shared = result.sharedRuntime();
        assertEquals(7, shared.slots());
        assertEquals(5, shared.heartbeatIndex("Worker-0"));
        assertEquals(6, shared.heartbeatIndex("Worker-1"));
        assertSame(shared.header(), result.physicalMemoryLayout().allocations().get(SharedRuntimeLayout.storageKey()));
    }

    @Test
    void fingerprintAndEpochAreDeterministicWhileOnlyEpochDependsOnCode() {
        SharedRuntimeLayoutPlanner.Result first = planner.plan(PhysicalMemoryLayout.empty(), "Main",
            List.of("Worker-0"), "code-a", profile, RuntimePreferences.defaults());
        SharedRuntimeLayoutPlanner.Result repeated = planner.plan(PhysicalMemoryLayout.empty(), "Main",
            List.of("Worker-0"), "code-a", profile, RuntimePreferences.defaults());
        SharedRuntimeLayoutPlanner.Result changedCode = planner.plan(PhysicalMemoryLayout.empty(), "Main",
            List.of("Worker-0"), "code-b", profile, RuntimePreferences.defaults());

        assertEquals(first.sharedRuntime().fingerprint(), repeated.sharedRuntime().fingerprint());
        assertEquals(first.sharedRuntime().epoch(), repeated.sharedRuntime().epoch());
        assertEquals(first.sharedRuntime().fingerprint(), changedCode.sharedRuntime().fingerprint());
        assertNotEquals(first.sharedRuntime().epoch(), changedCode.sharedRuntime().epoch());
    }

    @Test
    void appendsTheHeaderAfterExistingStorageWithoutOverlap() {
        PhysicalMemoryLayout.StorageKey existingKey = new PhysicalMemoryLayout.StorageKey(null, "values");
        PhysicalMemoryLayout.Allocation existing = new PhysicalMemoryLayout.Allocation(existingKey, 63,
            List.of(new PhysicalMemoryLayout.Slice(0, 0, 0, 63)));
        PhysicalMemoryLayout base = new PhysicalMemoryLayout(List.of(
            new PhysicalMemoryLayout.Segment("cell__mpl_mem0", RuntimePreferences.MemoryKind.CELL, 64, 63)
        ), Map.of(existingKey, existing), 63);
        RuntimePreferences preferences = new RuntimePreferences(RuntimePreferences.Goal.MIN_RESOURCES,
            RuntimePreferences.defaults().processors(), Map.of(RuntimePreferences.MemoryKind.CELL, 2));

        SharedRuntimeLayoutPlanner.Result result = planner.plan(base, "Main", List.of("Worker-0"),
            "code-a", profile, preferences);

        assertSame(existing, result.physicalMemoryLayout().allocations().get(existingKey));
        assertEquals(List.of(
            new PhysicalMemoryLayout.Slice(0, 63, 0, 1),
            new PhysicalMemoryLayout.Slice(1, 0, 1, 5)
        ), result.sharedRuntime().header().slices());
        assertEquals(69, result.physicalMemoryLayout().physicalSlots());
    }

    @Test
    void allocatesOwnedFixedWidthMailboxesAfterTheHeader() {
        List<SharedRuntimeLayoutPlanner.MailboxRequirement> mailboxes = List.of(
            new SharedRuntimeLayoutPlanner.MailboxRequirement("MainToWorker0", "Main", "Worker-0", 2),
            new SharedRuntimeLayoutPlanner.MailboxRequirement("Worker0ToMain", "Worker-0", "Main", 1));

        SharedRuntimeLayoutPlanner.Result result = planner.plan(PhysicalMemoryLayout.empty(), "Main",
            List.of("Worker-0"), mailboxes, "code-a", profile, RuntimePreferences.defaults());

        SharedRuntimeLayout shared = result.sharedRuntime();
        assertEquals(15, shared.slots());
        assertEquals(6, shared.headerSlots());
        assertEquals(2, shared.mailboxes().size());
        assertEquals(5, shared.mailbox("MainToWorker0").slots());
        assertEquals(4, shared.mailbox("Worker0ToMain").slots());
        assertEquals(15, result.physicalMemoryLayout().physicalSlots());
        assertSame(shared.mailbox("MainToWorker0").allocation(), result.physicalMemoryLayout().allocations()
            .get(SharedMailboxLayout.storageKey("MainToWorker0")));
    }

    @Test
    void mailboxShapeAffectsTheLayoutFingerprintAndEndpointsMustBelongToTheTopology() {
        SharedRuntimeLayoutPlanner.Result narrow = planner.plan(PhysicalMemoryLayout.empty(), "Main",
            List.of("Worker-0"), List.of(new SharedRuntimeLayoutPlanner.MailboxRequirement(
                "Requests", "Main", "Worker-0", 1)), "code-a", profile, RuntimePreferences.defaults());
        SharedRuntimeLayoutPlanner.Result wide = planner.plan(PhysicalMemoryLayout.empty(), "Main",
            List.of("Worker-0"), List.of(new SharedRuntimeLayoutPlanner.MailboxRequirement(
                "Requests", "Main", "Worker-0", 2)), "code-a", profile, RuntimePreferences.defaults());

        assertNotEquals(narrow.sharedRuntime().fingerprint(), wide.sharedRuntime().fingerprint());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> planner.plan(PhysicalMemoryLayout.empty(), "Main", List.of("Worker-0"),
                List.of(new SharedRuntimeLayoutPlanner.MailboxRequirement("Invalid", "Main", "Worker-9", 1)),
                "code-a", profile, RuntimePreferences.defaults()));
    }
}
