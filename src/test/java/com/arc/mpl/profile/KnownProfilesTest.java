package com.arc.mpl.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownProfilesTest {
    @Test
    void v146ProvidesTheBaselineLimits() {
        TargetProfile profile = KnownProfiles.find("v146").orElseThrow();

        assertEquals(64, profile.memoryCellCapacity());
        assertEquals(512, profile.memoryBankCapacity());
        assertEquals(2, profile.instructionsPerTick(TargetProfile.ProcessorKind.MICRO));
        assertEquals(8, profile.instructionsPerTick(TargetProfile.ProcessorKind.LOGIC));
        assertEquals(25, profile.instructionsPerTick(TargetProfile.ProcessorKind.HYPER));
        assertEquals(1_000, profile.maxInstructions());
        assertEquals(500, profile.maxJumpLabels());
        assertEquals(16, profile.maxTokensPerStatement());
        assertEquals(256, profile.maxGraphicsBufferCommands());
        assertEquals(1_024, profile.displayFlushCommandLimit());
        assertEquals(400, profile.maxMessageUtf16CodeUnits());
        assertEquals(1_023, profile.maxDrawCoordinateMagnitude());
        assertTrue(profile.capabilities().contains("baseline-logic"));
        assertFalse(profile.capabilities().contains("select"));
    }

    @Test
    void v1597ExposesItsAdditionalInstructions() {
        TargetProfile profile = KnownProfiles.find("v159.7").orElseThrow();

        assertTrue(profile.capabilities().containsAll(
            java.util.Set.of("select", "printchar", "format", "unpackcolor", "draw-print", "logic-build-variables")));
    }
}
