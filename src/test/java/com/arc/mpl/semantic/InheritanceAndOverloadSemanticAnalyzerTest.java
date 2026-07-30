package com.arc.mpl.semantic;

import com.arc.mpl.ast.Program;
import com.arc.mpl.hir.HirFunctionCall;
import com.arc.mpl.hir.HirMethodCall;
import com.arc.mpl.hir.HirVariableDeclaration;
import com.arc.mpl.hir.ObjectType;
import com.arc.mpl.syntax.MplSyntaxParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InheritanceAndOverloadSemanticAnalyzerTest {
    private final MplSyntaxParser parser = new MplSyntaxParser();
    private final SemanticAnalyzer analyzer = new SemanticAnalyzer();

    @Test
    void supportsSingleInheritanceSuperCovarianceAndVirtualOverride() {
        Program source = parser.parse("""
            class Animal {
                public value: Int;
                public fun Animal(value: Int) { this.value = value; }
                public fun score(amount: Int): Int { return this.value + amount; }
            }

            class Dog extends Animal {
                public bonus: Int;
                public fun Dog(value: Int, bonus: Int) {
                    super(value);
                    this.bonus = bonus;
                }
                public fun score(amount: Int): Int { return super.score(amount) + this.bonus; }
            }

            fun read(subject: Animal): Int { return subject.score(2); }

            val animal: Animal = new Dog(3, 4);
            val result = read(animal);
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(source, Path.of("main.mpl"));

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        var program = result.program().orElseThrow();
        assertEquals("Animal", program.classes().get(1).superClass().orElseThrow());
        HirVariableDeclaration animal = assertInstanceOf(HirVariableDeclaration.class, program.statements().get(0));
        assertEquals(new ObjectType("Animal", false), animal.type());
        var read = program.functions().stream().filter(function -> "read".equals(function.sourceName())).findFirst().orElseThrow();
        HirMethodCall call = assertInstanceOf(HirMethodCall.class,
            assertInstanceOf(com.arc.mpl.hir.HirReturn.class, read.body().get(0)).value().orElseThrow());
        assertEquals(2, call.dispatchTargets().size());
        assertEquals("__mpl_class_Animal_score", call.dispatchTargets().get(0).function());
        assertEquals("__mpl_class_Dog_score", call.dispatchTargets().get(1).function());
    }

    @Test
    void resolvesTopLevelAndMethodOverloadsByMostSpecificParameterType() {
        Program source = parser.parse("""
            class Animal { public fun Animal() {} }
            class Dog extends Animal { public fun Dog() { super(); } }

            fun classify(value: Animal): Int { return 1; }
            fun classify(value: Dog): Int { return 2; }

            class Printer {
                public fun Printer() {}
                public fun write(value: Animal): Int { return 3; }
                public fun write(value: Dog): Int { return 4; }
            }

            val dog = new Dog();
            val global = classify(dog);
            val printer = new Printer();
            val method = printer.write(dog);
            """, Path.of("main.mpl")).program().orElseThrow();

        SemanticResult result = analyzer.analyze(source, Path.of("main.mpl"));

        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        var program = result.program().orElseThrow();
        HirFunctionCall global = assertInstanceOf(HirFunctionCall.class,
            assertInstanceOf(HirVariableDeclaration.class, program.statements().get(1)).initializer());
        assertEquals("__mpl_overload_classify_1", global.function());
        HirMethodCall method = assertInstanceOf(HirMethodCall.class,
            assertInstanceOf(HirVariableDeclaration.class, program.statements().get(3)).initializer());
        assertEquals("__mpl_class_Printer_write_1", method.dispatchTargets().get(0).function());
    }

    @Test
    void rejectsInvalidInheritanceAndAmbiguousOverloadContracts() {
        assertDiagnostic("""
            class Child extends Missing { public fun Child() { super(); } }
            """, "MPL3710");
        assertDiagnostic("""
            class First extends Second { public fun First() { super(); } }
            class Second extends First { public fun Second() { super(); } }
            """, "MPL3711");
        assertDiagnostic("""
            class Parent { public fun Parent() {} }
            class Child extends Parent { public fun Child() {} }
            """, "MPL3715");
        assertDiagnostic("""
            class Parent {
                public fun Parent() {}
                public fun value(): Int { return 1; }
            }
            class Child extends Parent {
                public fun Child() { super(); }
                private fun value(): Int { return 2; }
            }
            """, "MPL3714");
        assertDiagnostic("""
            class Animal { public fun Animal() {} }
            class Dog extends Animal { public fun Dog() { super(); } }
            fun choose(left: Animal, right: Dog): Int { return 1; }
            fun choose(left: Dog, right: Animal): Int { return 2; }
            val answer = choose(new Dog(), new Dog());
            """, "MPL3511");
    }

    private void assertDiagnostic(String source, String expectedCode) {
        Program program = parser.parse(source, Path.of("main.mpl")).program().orElseThrow();
        SemanticResult result = analyzer.analyze(program, Path.of("main.mpl"));
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> expectedCode.equals(diagnostic.code())),
            () -> result.diagnostics().toString());
    }
}
