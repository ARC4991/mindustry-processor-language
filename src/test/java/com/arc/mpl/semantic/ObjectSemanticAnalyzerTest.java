package com.arc.mpl.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirExpressionStatement;
import com.arc.mpl.hir.HirNewObject;
import com.arc.mpl.hir.HirObjectFieldAssignment;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.ObjectType;
import com.arc.mpl.syntax.MplSyntaxParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectSemanticAnalyzerTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();
    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();

    @Test
    void lowersClassesToNominalTypesWithDistinctStaticAllocations() {
        SemanticResult result = analyze("""
            class Counter {
                private value: Int;

                public fun Counter(initial: Int) {
                    this.value = initial;
                }

                public fun add(amount: Int): Int {
                    this.value += amount;
                    return this.value;
                }
            }

            val first = new Counter(1);
            val second: Counter = new Counter(10);
            val firstValue = first.add(2);
            val secondValue = second.add(5);
            """);

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        var program = result.program().orElseThrow();
        assertEquals(1, program.classes().size());
        assertFalse(program.classes().get(0).exported());
        HirNewObject first = assertInstanceOf(HirNewObject.class,
            assertInstanceOf(HirVariableDeclaration.class, program.statements().get(0)).initializer());
        HirNewObject second = assertInstanceOf(HirNewObject.class,
            assertInstanceOf(HirVariableDeclaration.class, program.statements().get(1)).initializer());
        assertEquals(new ObjectType("Counter", false), first.type());
        assertEquals(1, first.allocationId());
        assertEquals(2, second.allocationId());
        HirFunctionCall call = assertInstanceOf(HirFunctionCall.class,
            assertInstanceOf(HirVariableDeclaration.class, program.statements().get(2)).initializer());
        assertEquals("__mpl_class_Counter_add", call.function());
        assertEquals(2, call.arguments().size());
        assertInstanceOf(HirObjectFieldAssignment.class,
            assertInstanceOf(HirExpressionStatement.class, program.functions().get(0).body().get(0)).expression());
    }

    @Test
    void rejectsMissingConstructorInitializationAndPrivateExternalAccess() {
        SemanticResult result = analyze("""
            class Pair {
                private left: Int;
                private right: Int;

                public fun Pair(value: Int) {
                    this.left = value;
                }
            }

            val pair = new Pair(1);
            val leaked = pair.left;
            """);

        assertTrue(result.program().isEmpty());
        assertTrue(hasDiagnostic(result, "MPL3704", "right"));
        assertTrue(hasDiagnostic(result, "MPL3707", "private"));
    }

    @Test
    void rejectsReadingAFieldBeforeConstructorInitialization() {
        SemanticResult result = analyze("""
            class Pair {
                private left: Int;
                private right: Int;

                public fun Pair(value: Int) {
                    this.left = this.right;
                    this.right = value;
                }
            }
            val pair = new Pair(1);
            """);

        assertTrue(result.program().isEmpty());
        assertTrue(hasDiagnostic(result, "MPL3704", "初始化前被读取"));
    }

    @Test
    void allowsReusableNonEscapingLocalAllocations() {
        SemanticResult result = analyze("""
            class Counter {
                public value: Int;
                public fun Counter(initial: Int) { this.value = initial; }
                public fun add(amount: Int): Int {
                    this.value += amount;
                    return this.value;
                }
            }

            fun calculate(seed: Int): Int {
                val local = new Counter(seed);
                return local.add(2);
            }

            while (true) {
                val item = new Counter(1);
                item.add(3);
                val same = item === item;
                break;
            }
            """);

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        assertTrue(result.program().isPresent());
    }

    @Test
    void rejectsLocalObjectAliasesReturnsArgumentsAndLeakingReceivers() {
        SemanticResult result = analyze("""
            fun consume(value: Counter) {}
            fun identity(value: Counter): Counter { return value; }

            class Counter {
                public fun Counter() {}
                public fun leak(): Counter { return this; }
            }

            fun createDirect(): Counter {
                return new Counter();
            }

            fun returnLocal(): Counter {
                val local = new Counter();
                return local;
            }

            fun misuse() {
                val item = new Counter();
                val alias = item;
                consume(item);
                val compared = identity(item) === item;
                item.leak();
                var mutable = new Counter();
            }
            """);

        assertTrue(result.program().isEmpty());
        assertEquals(7, result.diagnostics().stream()
            .filter(diagnostic -> "MPL3708".equals(diagnostic.code())).count());
    }

    @Test
    void rejectsReusableAllocationWhenTheConstructorLeaksThis() {
        SemanticResult result = analyze("""
            fun consume(value: Counter) {}

            class Counter {
                public fun Counter() { consume(this); }
            }

            fun create() {
                val item = new Counter();
            }
            """);

        assertTrue(result.program().isEmpty());
        assertTrue(hasDiagnostic(result, "MPL3708", "构造器 Counter 会泄露 this"));
    }

    @Test
    void narrowsNullableObjectReferences() {
        SemanticResult result = analyze("""
            class Counter {
                public value: Int;
                public fun Counter(value: Int) { this.value = value; }
            }

            val actual = new Counter(1);
            val optional: Counter? = actual;
            if (optional != null) {
                val value = optional.value;
            }
            """);

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
    }

    @Test
    void reservesValueEqualityAndProvidesExplicitObjectIdentity() {
        SemanticResult valid = analyze("""
            class Marker { public fun Marker() {} }
            val first = new Marker();
            val second = new Marker();
            val same = first === first;
            val different = first !== second;
            """);
        assertTrue(valid.diagnostics().isEmpty(), () -> valid.diagnostics().toString());

        SemanticResult invalid = analyze("""
            class Marker { public fun Marker() {} }
            val first = new Marker();
            val second = new Marker();
            val same = first == second;
            """);
        assertTrue(invalid.program().isEmpty());
        assertTrue(hasDiagnostic(invalid, "MPL3103", "不能比较"));
    }

    private SemanticResult analyze(String source) {
        Program program = parser.parse(source, Path.of("main.mpl")).program().orElseThrow();
        return analyzer.analyze(program, Path.of("main.mpl"));
    }

    private boolean hasDiagnostic(SemanticResult result, String code, String messagePart) {
        return result.diagnostics().stream().anyMatch(diagnostic -> code.equals(diagnostic.code())
            && diagnostic.message().contains(messagePart));
    }
}
