package com.arc.mpl.profile;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetProfileLoaderTest {
    @Test
    void loadsVersionedProfileDocument() {
        TargetProfile profile = TargetProfileLoader.load(json("v-test", 1));

        assertEquals("v-test", profile.id());
        assertEquals(64, profile.memoryCellCapacity());
        assertEquals(512, profile.memoryBankCapacity());
        assertEquals(8, profile.instructionsPerTick(TargetProfile.ProcessorKind.LOGIC));
        assertTrue(profile.capabilities().contains("baseline-logic"));
        assertEquals("duo", profile.buildingType("Duo").orElseThrow().mlogName());
        assertEquals(1, profile.instructions().size());
        assertEquals("@io.print", profile.macros().get(0).name());
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> TargetProfileLoader.load(json("v-test", 2)));

        assertTrue(error.getMessage().contains("schemaVersion"));
    }

    @Test
    void rejectsMissingRequiredProfileSections() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> TargetProfileLoader.load(bytes("""
                {"schemaVersion": 1, "id": "v-test", "capabilities": [], "processors": {}, "hardware": {}}
                """)));

        assertTrue(error.getMessage().contains("micro"));
    }

    private static ByteArrayInputStream json(String id, int schemaVersion) {
        return bytes("""
            {
              "schemaVersion": %d,
              "id": "%s",
              "capabilities": ["baseline-logic"],
              "limits": {
                "maxInstructions": 1000,
                "maxJumpLabels": 500,
                "maxTokensPerStatement": 16,
                "maxGraphicsBufferCommands": 256,
                "displayFlushCommandLimit": 1024,
                "maxMessageUtf16CodeUnits": 400,
                "maxDrawCoordinateMagnitude": 1023
              },
              "processors": {
                "micro": { "ipt": 2 },
                "logic": { "ipt": 8 },
                "hyper": { "ipt": 25 }
              },
              "hardware": {
                "memoryCell": { "capacity": 64 },
                "memoryBank": { "capacity": 512 }
              },
              "instructions": [
                { "opcode": "print", "operands": ["value"], "permissions": ["normal"] }
              ],
              "contents": {
                "units": [],
                "buildings": [
                  { "mplType": "Duo", "mlogName": "duo", "fields": [], "actions": [] }
                ]
              },
              "macros": [
                {
                  "name": "@io.print", "parameters": [], "effects": ["writesMessage"],
                  "maxCost": { "instructions": 1, "virtualSlots": 0, "physicalSlots": 0 },
                  "lowering": ["print"]
                }
              ]
            }
            """.formatted(schemaVersion, id));
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
