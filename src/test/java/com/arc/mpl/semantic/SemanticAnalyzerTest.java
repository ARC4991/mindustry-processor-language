package com.arc.mpl.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.CollectionType;
import com.arc.mpl.hir.HirDynamicCollectionSet;
import com.arc.mpl.hir.HirDynamicIndexAccess;
import com.arc.mpl.hir.HirFor;
import com.arc.mpl.hir.HirIf;
import com.arc.mpl.hir.HirMemberAccess;
import com.arc.mpl.hir.HirUnitControl;
import com.arc.mpl.hir.HirUnitIteration;
import com.arc.mpl.hir.HirUnitQuery;
import com.arc.mpl.hir.HirUnitQueryGet;
import com.arc.mpl.hir.HirUnitQuerySize;
import com.arc.mpl.hir.UnitSetType;
import com.arc.mpl.hir.UnitType;
import com.arc.mpl.syntax.MplSyntaxParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticAnalyzerTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();
    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();

    @Test
    void rejectsImplicitBoolToIntConversion() {
        Program program = parser.parse("var number: Int = true;", Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals("MPL3103", result.diagnostics().get(0).code());
    }

    @Test
    void rejectsReassignmentOfVal() {
        Program program = parser.parse("val enabled = true;\nenabled = false;", Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals("MPL3104", result.diagnostics().get(0).code());
    }

    @Test
    void retainsVarAndValMutabilityInStructuredHir() {
        Program program = parser.parse("var mutable: Int = 1;\nval fixed: Int = 2;", Path.of("main.mpl"))
            .program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        HirVariableDeclaration mutable = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        HirVariableDeclaration fixed = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(1));
        assertTrue(mutable.mutable());
        assertFalse(fixed.mutable());
    }

    @Test
    void rejectsBreakAndContinueOutsideLoops() {
        Program program = parser.parse("break;\ncontinue;", Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals(java.util.List.of("MPL3401", "MPL3402"),
            result.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
    }

    @Test
    void keepsCountingForInitializerInsideTheLoopScope() {
        Program program = parser.parse("for (var i: Int = 0; i < 2; i += 1) { }\nvar leaked: Int = i;",
            Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3102")
            && diagnostic.message().contains("i")));
    }

    @Test
    void rejectsRecursiveCallsAndMissingReturnPaths() {
        Program program = parser.parse("""
            fun first(value: Int): Int {
                return second(value);
            }
            fun second(value: Int): Int {
                if (value > 0) {
                    return first(value - 1);
                }
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3504")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3505")));
    }

    @Test
    void rejectsReturnAtTopLevel() {
        Program program = parser.parse("return 1;", Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals("MPL3502", result.diagnostics().get(0).code());
    }

    @Test
    void allowsFunctionToReadAnEarlierInitializedGlobal() {
        Program program = parser.parse("""
            var scale: Int = 2;
            fun multiply(value: Int): Int {
                return value * scale;
            }
            var result: Int = multiply(3);
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void rejectsFunctionAccessToAGlobalDeclaredAfterIt() {
        Program program = parser.parse("""
            fun readScale(): Int {
                return scale;
            }
            var scale: Int = 2;
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3506")
            && diagnostic.message().contains("scale")));
    }

    @Test
    void rejectsTopLevelCallBeforeAnIndirectGlobalDependencyIsInitialized() {
        Program program = parser.parse("""
            var result: Int = outer();
            var source: Int = 7;
            fun inner(): Int {
                return source;
            }
            fun outer(): Int {
                return inner();
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3507")
            && diagnostic.message().contains("source")));
    }

    @Test
    void allowsTopLevelCallAfterAnIndirectGlobalDependencyIsInitialized() {
        Program program = parser.parse("""
            var source: Int = 7;
            var result: Int = outer();
            fun inner(): Int {
                return source;
            }
            fun outer(): Int {
                return inner();
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void rejectsReadingAGlobalFromItsOwnFunctionCallInitializer() {
        Program program = parser.parse("""
            var source: Int = readSource();
            fun readSource(): Int {
                return source;
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3507")
            && diagnostic.message().contains("source")));
    }

    @Test
    void rejectsUnitSetTraversalInsideAFunction() {
        Program program = parser.parse("""
            fun controlUnits() {
                for (var unit : Unit.getAllDagger()) {
                    unit.stop();
                }
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3508")));
    }

    @Test
    void infersHomogeneousArrayElementTypeAndChecksStaticIndexBounds() {
        Program program = parser.parse("""
            val values = [1, 2, 3];
            var selected: Int = values[1];
            var count: Int = values.size;
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        HirVariableDeclaration values = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        assertEquals(new CollectionType(CollectionType.Kind.ARRAY, com.arc.mpl.hir.ValueType.INT), values.type());
    }

    @Test
    void rejectsDynamicAndOutOfRangeAggregateIndexes() {
        Program program = parser.parse("""
            val values: Int[] = [1, 2, 3];
            var index: Int = 1;
            var dynamic: Int = values[index];
            var invalid: Int = values[3];
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals(2, result.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("MPL3601")).count());
    }

    @Test
    void provesDynamicArrayReadsAndWritesInAStandardCountingLoop() {
        Program program = parser.parse("""
            var values: Int[] = [1, 2, 3];
            for (var i: Int = 0; i < values.size; i += 1) {
                var current: Int = values[i];
                values.set(i, current + 1);
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.diagnostics().isEmpty());
        HirFor loop = assertInstanceOf(HirFor.class, result.program().orElseThrow().statements().get(1));
        HirVariableDeclaration current = assertInstanceOf(HirVariableDeclaration.class, loop.body().get(0));
        assertInstanceOf(HirDynamicIndexAccess.class, current.initializer());
        assertInstanceOf(HirDynamicCollectionSet.class, loop.body().get(1));
    }

    @Test
    void rejectsDynamicArrayIndexesWithoutTheExactBoundsProof() {
        Program program = parser.parse("""
            var values: Int[] = [1, 2, 3];
            var index: Int = 1;
            var outside: Int = values[index];
            for (var i: Int = 0; i <= values.size; i += 1) {
                values.set(i, 0);
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertEquals(2, result.diagnostics().stream()
            .filter(diagnostic -> diagnostic.code().equals("MPL3601")
                && diagnostic.message().contains("无法证明动态 Array 下标"))
            .count());
    }

    @Test
    void rejectsMutatingAProvenLoopIndexInsideItsBody() {
        Program program = parser.parse("""
            var values: Int[] = [1, 2, 3];
            for (var i: Int = 0; i < values.size; i += 1) {
                i = 2;
                var current: Int = values[i];
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3601")
            && diagnostic.message().contains("修改下标变量")));
    }

    @Test
    void supportsStaticListAndSetOperationsAndTraversal() {
        Program program = parser.parse("""
            val list: List<Int> = listOf(1, 2, 3);
            val set: Set<Int> = Set.of(2, 4, 6);
            var first: Int = list.get(0);
            var found: Bool = set.contains(4);
            for (var value : list) {
                first = value;
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isPresent());
        HirVariableDeclaration list = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        HirVariableDeclaration set = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(1));
        assertEquals(new CollectionType(CollectionType.Kind.LIST, com.arc.mpl.hir.ValueType.INT), list.type());
        assertEquals(new CollectionType(CollectionType.Kind.SET, com.arc.mpl.hir.ValueType.INT), set.type());
    }

    @Test
    void resolvesEmptyCollectionsFromAnExplicitTypeAndRejectsAggregateCopies() {
        Program validProgram = parser.parse("""
            val emptyArray: Int[] = [];
            val emptyList: List<Int> = List.of();
            val emptySet: Set<Int> = setOf();
            var size: Int = emptyArray.size + emptyList.size + emptySet.size;
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult validResult = analyzer.analyze(validProgram, Path.of("main.mpl"));

        assertTrue(validResult.program().isPresent());

        Program invalidProgram = parser.parse("""
            var source: Int[] = [1, 2];
            var copy = source;
            source = [3, 4];
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult invalidResult = analyzer.analyze(invalidProgram, Path.of("main.mpl"));

        assertTrue(invalidResult.program().isEmpty());
        assertEquals(2, invalidResult.diagnostics().stream().filter(diagnostic -> "MPL3601".equals(diagnostic.code())).count());
    }

    @Test
    void rejectsNestedStaticAggregateLayoutsUntilMemoryRuntimeExists() {
        Program program = parser.parse("""
            val matrix: Int[][] = [[1, 2], [3, 4]];
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3601".equals(diagnostic.code())));
    }

    @Test
    void infersAndReusesLazyTypedUnitSets() {
        Program program = parser.parse("""
            val active = Unit.getAllDagger().where(_.alive).where(unit => unit.health > 0.0);
            val same: Set<Unit<Dagger>> = active.where(_.ammo > 0.0);
            var count: Int = same.size;
            for (var unit : same) {
                unit.move(1.0, 2.0);
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.diagnostics().isEmpty());
        HirVariableDeclaration active = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(0));
        HirVariableDeclaration same = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(1));
        HirVariableDeclaration count = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(2));
        assertEquals(new UnitSetType("Dagger"), active.type());
        assertEquals(active.type(), same.type());
        assertInstanceOf(HirUnitQuery.class, active.initializer());
        assertInstanceOf(HirUnitQuerySize.class, count.initializer());
        HirUnitIteration iteration = assertInstanceOf(HirUnitIteration.class,
            result.program().orElseThrow().statements().get(3));
        assertEquals(3, iteration.filters().size());
    }

    @Test
    void rejectsMutableOrUnsafelyScannedUnitSets() {
        Program program = parser.parse("""
            var mutable = Unit.getAllDagger();
            val managed = Unit.getAllDagger().take(3);
            var managedSize: Int = managed.size;
            fun countUnits(): Int {
                val local = Unit.getAllDagger();
                return mutable.size;
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3301")
            && diagnostic.message().contains("只能使用 val")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3307")
            && diagnostic.message().contains("take(n)")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL3508")));
    }

    @Test
    void getsAndSmartCastsANullablePersistentUnitReference() {
        Program program = parser.parse("""
            val active = Unit.getAllDagger().where(_.alive);
            val leader = active.get(0);
            if (leader != null) {
                val health: Float = leader.health;
                leader.move(4.0, 8.0);
            }
            val fallback: Unit<Dagger>? = null;
            if (fallback == null) {
                val absent: Bool = true;
            } else {
                val alive: Bool = fallback.alive;
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.diagnostics().isEmpty());
        HirVariableDeclaration leader = assertInstanceOf(HirVariableDeclaration.class,
            result.program().orElseThrow().statements().get(1));
        assertEquals(new UnitType("Dagger", true), leader.type());
        assertInstanceOf(HirUnitQueryGet.class, leader.initializer());
        HirIf branch = assertInstanceOf(HirIf.class, result.program().orElseThrow().statements().get(2));
        HirVariableDeclaration health = assertInstanceOf(HirVariableDeclaration.class, branch.thenBody().get(0));
        HirMemberAccess read = assertInstanceOf(HirMemberAccess.class, health.initializer());
        assertEquals(new UnitType("Dagger", false), read.target().type());
        HirUnitControl move = assertInstanceOf(HirUnitControl.class, branch.thenBody().get(1));
        assertTrue(move.storedReference());
    }

    @Test
    void rejectsUnsafeNullableUnitReferenceUses() {
        Program program = parser.parse("""
            val leader = Unit.getAllDagger().get(0);
            leader.move(1.0, 2.0);
            var mutable = leader;
            if (mutable != null) {
                val health = mutable.health;
            }
            val unknown = null;
            val wrong: Unit<Alpha>? = leader;
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3308".equals(diagnostic.code())));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3103".equals(diagnostic.code())
            && diagnostic.message().contains("仅从 null")));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3103".equals(diagnostic.code())
            && diagnostic.message().contains("Unit<Alpha>?")));
    }

    @Test
    void rejectsManagedNestedAndFunctionUnitGets() {
        Program program = parser.parse("""
            val managed = Unit.getAllDagger().take(3);
            val selected = managed.get(0);
            fun forbidden(): Unit<Dagger>? {
                return Unit.getAllDagger().get(0);
            }
            for (var unit : Unit.getAllDagger()) {
                val nested = Unit.getAllDagger().get(0);
            }
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));

        assertTrue(result.program().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3307".equals(diagnostic.code())));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3306".equals(diagnostic.code())));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3508".equals(diagnostic.code())));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> "MPL3602".equals(diagnostic.code())));
    }
}
