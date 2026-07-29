package com.arc.mpl.project;

import com.arc.mpl.ast.Program;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectProgramLoaderTest {
    private final TargetProfile profile = KnownProfiles.find("v146").orElseThrow();
    private final HardwareContract hardware = new HardwareContract(List.of(), Map.of());

    @Test
    void linksMixedMplAndMilModulesWithPrivateSymbolIsolation(@TempDir Path project) throws Exception {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("main.mpl"), """
            import { doubleValue } from "./double";
            import { tripleValue } from "./triple.mil";
            val result: Int = doubleValue(2) + tripleValue(3);
            """);
        Files.writeString(source.resolve("double.mpl"), """
            val factor: Int = 2;
            export fun doubleValue(value: Int): Int { return value * factor; }
            """);
        Files.writeString(source.resolve("triple.mil"), """
            val factor: Int = 3;
            export fun tripleValue(value: Int): Int { return value * factor; }
            """);
        ProjectSourceCatalog catalog = new ProjectSourceLoader().load(project);

        ProjectProgramResult linked = new ProjectProgramLoader().load(catalog, profile, hardware);

        assertTrue(linked.succeeded(), () -> linked.diagnostics().toString());
        assertEquals(List.of(source.resolve("double.mpl").toAbsolutePath().normalize(),
            source.resolve("triple.mil").toAbsolutePath().normalize(),
            source.resolve("main.mpl").toAbsolutePath().normalize()), linked.modules());
        var program = linked.program().orElseThrow();
        assertEquals(2, program.functions().size());
        assertTrue(program.functions().stream().allMatch(function -> function.name().startsWith("__module_")));
        assertEquals(3, program.statements().size());
        assertTrue(new SemanticAnalyzer(profile).analyze(program, catalog.entryFile(), hardware).program().isPresent());
    }

    @Test
    void linksExportedClassesAndRewritesObjectTypes(@TempDir Path project) throws Exception {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("main.mpl"), """
            import { Counter } from "./counter";
            val counter: Counter = new Counter(4);
            val result = counter.get();
            """);
        Files.writeString(source.resolve("counter.mil"), """
            export class Counter {
                private value: Int;
                public fun Counter(initial: Int) { this.value = initial; }
                public fun get(): Int { return this.value; }
            }
            """);

        ProjectSourceCatalog catalog = new ProjectSourceLoader().load(project);
        ProjectProgramResult linked = new ProjectProgramLoader().load(catalog, profile, hardware);

        assertTrue(linked.succeeded(), () -> linked.diagnostics().toString());
        Program program = linked.program().orElseThrow();
        assertEquals(1, program.classes().size());
        assertTrue(program.classes().get(0).name().startsWith("__module_"));
        var semantic = new SemanticAnalyzer(profile).analyze(program, catalog.entryFile(), hardware);
        assertTrue(semantic.program().isPresent(), () -> semantic.diagnostics().toString());
    }

    @Test
    void rejectsCyclesAndImportsOfPrivateNames(@TempDir Path project) throws Exception {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("main.mpl"), "import { helper } from \"./a\"; helper();\n");
        Files.writeString(source.resolve("a.mpl"), """
            import { entry } from "./main";
            fun helper(): Void { }
            export fun entry(): Void { }
            """);

        ProjectProgramResult cycle = load(project);

        assertFalse(cycle.succeeded());
        assertTrue(cycle.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1404")));

        Files.writeString(source.resolve("a.mpl"), "fun helper(): Void { }\n");
        ProjectProgramResult privateImport = load(project);
        assertFalse(privateImport.succeeded());
        assertTrue(privateImport.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1409")));
    }

    @Test
    void rejectsAmbiguousEscapingAndExternalImports(@TempDir Path project) throws Exception {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("main.mpl"), "import { value } from \"./shared\";\n");
        Files.writeString(source.resolve("shared.mpl"), "export val value: Int = 1;\n");
        Files.writeString(source.resolve("shared.mil"), "export val value: Int = 2;\n");

        ProjectProgramResult ambiguous = load(project);
        assertTrue(ambiguous.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1405")));

        Files.writeString(source.resolve("main.mpl"), "import { value } from \"../../outside\";\n");
        ProjectProgramResult escaping = load(project);
        assertTrue(escaping.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1403")));

        Files.writeString(source.resolve("main.mpl"), "import { value } from \"@mpl/example\";\n");
        ProjectProgramResult external = load(project);
        assertTrue(external.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1401")));
    }

    @Test
    void rejectsMutableExports(@TempDir Path project) throws Exception {
        Path source = Files.createDirectories(project.resolve("src"));
        Files.writeString(source.resolve("main.mpl"), "import { counter } from \"./state\";\n");
        Files.writeString(source.resolve("state.mpl"), "export var counter: Int = 0;\n");

        ProjectProgramResult result = load(project);

        assertFalse(result.succeeded());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL1411")));
    }

    private ProjectProgramResult load(Path project) throws Exception {
        ProjectSourceCatalog catalog = new ProjectSourceLoader().load(project);
        return new ProjectProgramLoader().load(catalog, profile, hardware);
    }
}
