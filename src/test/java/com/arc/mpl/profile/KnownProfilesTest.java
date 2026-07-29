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
        assertEquals("dagger", profile.unitType("Dagger").orElseThrow().mlogName());
        assertEquals(com.arc.mpl.hir.ValueType.FLOAT, profile.unitPropertyType("health").orElseThrow());
        assertEquals(java.util.List.of(com.arc.mpl.hir.ValueType.FLOAT, com.arc.mpl.hir.ValueType.FLOAT),
            profile.unitAction("move").orElseThrow().parameterTypes());
        assertEquals("duo", profile.buildingType("Duo").orElseThrow().mlogName());
        assertTrue(profile.instructions().stream().anyMatch(instruction -> instruction.opcode().equals("ubind")));
        assertEquals(java.util.Set.of(
                "@unit.each", "@unit.eachManaged", "@unit.read", "@unit.alive", "@unit.move",
                "@building.read", "@building.control", "@io.print", "@io.draw", "@io.drawFlush"),
            profile.macros().stream().map(TargetProfile.Macro::name).collect(java.util.stream.Collectors.toSet()));
        assertEquals(TargetProfile.MacroVisibility.RUNTIME_PRIVATE,
            profile.macro("@io.drawFlush").orElseThrow().visibility());
        assertTrue(profile.macros().stream()
            .filter(macro -> !macro.name().equals("@io.drawFlush"))
            .allMatch(macro -> macro.visibility() == TargetProfile.MacroVisibility.PUBLIC));
        assertTrue(java.util.Set.of("read", "write", "control", "stop").stream()
            .allMatch(opcode -> profile.instructions().stream().anyMatch(instruction -> instruction.opcode().equals(opcode))));
    }

    @Test
    void v1597ExposesItsAdditionalInstructions() {
        TargetProfile profile = KnownProfiles.find("v159.7").orElseThrow();

        assertTrue(profile.capabilities().containsAll(
            java.util.Set.of("select", "printchar", "format", "unpackcolor", "draw-print", "logic-build-variables")));
    }

    @Test
    void rejectsUnknownOrMissingProfile() {
        assertTrue(KnownProfiles.find("v999").isEmpty());
        assertTrue(KnownProfiles.find(null).isEmpty());
    }
}
