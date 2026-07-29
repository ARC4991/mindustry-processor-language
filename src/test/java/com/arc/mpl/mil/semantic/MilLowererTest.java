package com.arc.mpl.mil.semantic;

import com.arc.mpl.hir.HirDraw;
import com.arc.mpl.hir.HirPrintStatement;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.mil.syntax.MilParseResult;
import com.arc.mpl.mil.syntax.MilSourceKind;
import com.arc.mpl.mil.syntax.MilSyntaxParser;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.HardwareContract;
import com.arc.mpl.semantic.SemanticAnalyzer;
import com.arc.mpl.semantic.SemanticResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilLowererTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
    private final Path sourceFile = Path.of("src/main.mil");

    @Test
    void lowersPublicMacrosIntoTheSharedTypedHir() {
        String source = """
            @unit.eachManaged(@dagger, unit, 3, @unit.alive(unit)) {
                @unit.move(unit, 1.0, 2.0);
            }
            @io.print(@message1, "ready");
            @io.draw(@display1, clear, 0, 0, 0);
            """;
        HardwareContract hardware = new HardwareContract(List.of(
            new HardwareContract.LinkDeclaration("Status", "Message", "message1"),
            new HardwareContract.LinkDeclaration("Canvas", "Display", "display1")
        ), Map.of("Status", "message1"));
        MilParseResult parsed = new MilSyntaxParser().parse(source, sourceFile, profile, MilSourceKind.USER);

        MilLoweringResult lowered = new MilLowerer().lower(parsed.document().orElseThrow(), sourceFile, profile, hardware);
        SemanticResult analyzed = new SemanticAnalyzer(profile)
            .analyze(lowered.program().orElseThrow(), sourceFile, hardware);

        assertTrue(analyzed.program().isPresent(), () -> analyzed.diagnostics().toString());
        assertInstanceOf(HirUnitIteration.class, analyzed.program().orElseThrow().statements().get(0));
        assertInstanceOf(HirPrintStatement.class, analyzed.program().orElseThrow().statements().get(1));
        assertInstanceOf(HirDraw.class, analyzed.program().orElseThrow().statements().get(2));
    }
}
