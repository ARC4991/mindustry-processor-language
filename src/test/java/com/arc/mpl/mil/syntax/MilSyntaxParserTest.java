package com.arc.mpl.mil.syntax;

import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilSyntaxParserTest {
    private final MilSyntaxParser parser = new MilSyntaxParser();
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();

    @Test
    void preservesModuleDeclarationsInStructuredMil() {
        MilParseResult result = parser.parse("""
            import { clamp } from "./math";
            export fun normalized(value: Float): Float { return clamp(value); }
            """, Path.of("main.mil"), profile, MilSourceKind.USER);

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        var program = result.document().orElseThrow().program();
        assertEquals("./math", program.imports().get(0).source());
        assertEquals(java.util.List.of("clamp"), program.imports().get(0).names());
        assertEquals("normalized", program.exports().get(0).name());
    }

    @Test
    void parsesStructuredControlFlowMacrosAndGameSymbols() {
        MilParseResult result = parser.parse("""
            var phase: Float = 0.0;
            while (true) {
                @unit.eachManaged(@dagger, unit, 3, @unit.alive(unit)) {
                    @unit.move(unit, phase, 20.0);
                }
                @io.print(@message1, "phase=", phase);
            }
            """, Path.of("main.mil"), profile, MilSourceKind.USER);

        assertTrue(result.succeeded());
        assertEquals(4, result.document().orElseThrow().macroCalls().size());
        assertEquals("@unit.eachManaged", result.document().orElseThrow().macroCalls().get(0).name());
        assertTrue(result.document().orElseThrow().macroCalls().get(0).hasBody());
        assertEquals(java.util.List.of("dagger", "message1"), result.document().orElseThrow().gameSymbols().stream()
            .map(MilDocument.GameSymbol::name).toList());
        assertEquals(2, result.document().orElseThrow().program().statements().size());
        assertTrue(result.document().orElseThrow().program().statements().get(1)
            instanceof com.arc.mpl.ast.WhileStatement);
    }

    @Test
    void allowsRuntimePrivateFlushOnlyInCompilerGeneratedMil() {
        String source = "@io.draw(@display1, rect, 0, 0, 8, 8);\n@io.drawFlush(@display1);\n";

        MilParseResult user = parser.parse(source, Path.of("main.mil"), profile, MilSourceKind.USER);
        MilParseResult generated = parser.parse(source, Path.of("Main.mil"), profile, MilSourceKind.GENERATED);

        assertFalse(user.succeeded());
        assertTrue(user.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MIL3002")));
        assertTrue(generated.succeeded());
    }

    @Test
    void rejectsUnknownMacrosInvalidShapesAndRawMlog() {
        MilParseResult unknown = parser.parse("@logic.set(value, 1);", Path.of("main.mil"), profile,
            MilSourceKind.USER);
        MilParseResult missingBody = parser.parse("@unit.each(@dagger, unit);", Path.of("main.mil"), profile,
            MilSourceKind.USER);
        MilParseResult rawMlog = parser.parse("set value 1", Path.of("main.mil"), profile,
            MilSourceKind.USER);

        assertTrue(unknown.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MIL3001")));
        assertTrue(missingBody.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MIL3004")));
        assertTrue(rawMlog.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MIL2001")));
    }

    @Test
    void checksFixedAndVariadicArgumentCountsFromTheProfile() {
        MilParseResult fixed = parser.parse("@unit.move(unit, 1.0);", Path.of("main.mil"), profile,
            MilSourceKind.USER);
        MilParseResult variadic = parser.parse("@io.print();", Path.of("main.mil"), profile,
            MilSourceKind.USER);

        assertTrue(fixed.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MIL3003")));
        assertTrue(variadic.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MIL3003")));
    }

    @Test
    void acceptsTheProfileControlledUnitCountMacro() {
        MilParseResult result = parser.parse(
            "var count: Int = @unit.count(@dagger, unit, @unit.alive(unit));",
            Path.of("main.mil"), profile, MilSourceKind.USER);

        assertTrue(result.succeeded());
        assertEquals("@unit.count", result.document().orElseThrow().macroCalls().get(0).name());
    }

    @Test
    void acceptsNullableUnitRefsAndTheirPublicMacros() {
        MilParseResult result = parser.parse("""
            val leader: Unit<Dagger>? = @unit.get(@dagger, unit, 0, @unit.alive(unit));
            if (leader != null) {
                val health: Float = @unit.refRead(leader, health);
                @unit.refMove(leader, 1.0, 2.0);
            }
            """, Path.of("main.mil"), profile, MilSourceKind.USER);

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertEquals(java.util.List.of("@unit.get", "@unit.alive", "@unit.refRead", "@unit.refMove"),
            result.document().orElseThrow().macroCalls().stream().map(MilDocument.MacroCall::name).toList());
    }

    @Test
    void acceptsBuildingQueryMacros() {
        MilParseResult result = parser.parse("""
            val count: Int = @building.count(@duo, building, @building.read(building, enabled));
            val first: Building<Duo>? = @building.get(@duo, building, 0, @building.read(building, enabled));
            @building.each(@duo, building, @building.read(building, enabled)) {
                @building.control(building, enabled, false);
            }
            """, Path.of("main.mil"), profile, MilSourceKind.USER);

        assertTrue(result.succeeded(), () -> result.diagnostics().toString());
        assertTrue(result.document().orElseThrow().macroCalls().stream().map(MilDocument.MacroCall::name)
            .collect(java.util.stream.Collectors.toSet()).containsAll(java.util.Set.of(
                "@building.count", "@building.get", "@building.each", "@building.read", "@building.control")));
    }
}
