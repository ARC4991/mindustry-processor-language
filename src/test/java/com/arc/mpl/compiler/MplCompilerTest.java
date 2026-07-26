package com.arc.mpl.compiler;

import com.arc.mpl.diagnostic.Severity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MplCompilerTest {
    private final MplCompiler compiler = new MplCompiler();

    @Test
    void reportsAnUnknownTargetBeforeReadingTheProject() {
        CompilationResult result = compiler.compile(new CompilationRequest(Path.of("demo"), "v999"));

        assertFalse(result.succeeded());
        assertTrue(result.profile().isEmpty());
        assertEquals("MPL1001", result.diagnostics().get(0).code());
        assertEquals(Severity.ERROR, result.diagnostics().get(0).severity());
    }

    @Test
    void reportsTheFrontendPlaceholderForAKnownTarget() {
        CompilationResult result = compiler.compile(new CompilationRequest(Path.of("demo"), "v146"));

        assertFalse(result.succeeded());
        assertEquals("v146", result.profile().orElseThrow().id());
        assertEquals("MPL9001", result.diagnostics().get(0).code());
        assertTrue(result.mlog().isEmpty());
    }
}
