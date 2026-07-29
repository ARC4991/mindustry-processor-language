package com.arc.mpl.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.hir.BuildingType;
import com.arc.mpl.hir.HirBuildingIteration;
import com.arc.mpl.hir.HirBuildingQuery;
import com.arc.mpl.hir.HirBuildingQueryGet;
import com.arc.mpl.hir.HirBuildingQuerySize;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.LinkedBuildingSetType;
import com.arc.mpl.project.HardwareContract;
import com.arc.mpl.syntax.MplSyntaxParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingSetSemanticAnalyzerTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();
    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();

    @Test
    void savesCountsGetsAndReusesATypedLinkedBuildingSet() {
        Program program = parser.parse("""
            val turrets: LinkedBuildingSet<Duo> = Building.getAllDuo().where(_.enabled);
            val count = turrets.size;
            val first: Building<Duo>? = turrets.get(0);
            if (first != null) {
                val health = first.health;
                first.setEnabled(false);
            }
            for (var turret : turrets.where(_.health > 0.0)) {
                turret.shoot(1.0, 2.0, true);
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"), hardware());

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        HirVariableDeclaration turrets = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        HirBuildingQuery query = assertInstanceOf(HirBuildingQuery.class, turrets.initializer());
        assertEquals(new LinkedBuildingSetType("Duo"), turrets.type());
        assertEquals(List.of("duo1", "duo2"), query.buildings().stream().map(link -> link.gameAlias()).toList());

        HirBuildingQuerySize size = assertInstanceOf(HirBuildingQuerySize.class,
            assertInstanceOf(HirVariableDeclaration.class,
                result.program().orElseThrow().statements().get(1)).initializer());
        HirBuildingQueryGet get = assertInstanceOf(HirBuildingQueryGet.class,
            assertInstanceOf(HirVariableDeclaration.class,
                result.program().orElseThrow().statements().get(2)).initializer());
        assertEquals(new BuildingType("Duo", true), get.type());
        assertEquals(query.buildings(), size.query().buildings());
        assertEquals(query.buildings(), get.query().buildings());

        HirIf branch = assertInstanceOf(HirIf.class, result.program().orElseThrow().statements().get(3));
        HirVariableDeclaration health = assertInstanceOf(HirVariableDeclaration.class, branch.thenBody().get(0));
        assertEquals(new BuildingType("Duo", false),
            ((com.arc.mpl.hir.HirMemberAccess) health.initializer()).target().type());
        HirBuildingIteration iteration = assertInstanceOf(HirBuildingIteration.class,
            result.program().orElseThrow().statements().get(4));
        assertEquals(2, iteration.filters().size());
    }

    private HardwareContract hardware() {
        return new HardwareContract(List.of(
            new HardwareContract.LinkDeclaration("North", "Duo", "duo1"),
            new HardwareContract.LinkDeclaration("South", "Duo", "duo2")
        ), Map.of());
    }
}
