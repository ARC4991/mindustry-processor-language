package com.arc.mpl.diagnostic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticMessagesTest {
    @Test
    void rendersTheChineseCatalogueByDefault() {
        String message = DiagnosticMessages.formatChinese(
            "compiler.target.unsupported", List.of("v999"), "fallback");

        assertEquals("不支持的 Mindustry target profile：v999", message);
        assertEquals("zh-CN", DiagnosticMessages.localeTag(DiagnosticLanguage.ZH_CN));
    }

    @Test
    void keepsTheSourceMessageWhenANewCatalogueLacksAKey() {
        String message = DiagnosticMessages.formatChinese("unknown.key", List.of(), "保留的中文回退");

        assertEquals("保留的中文回退", message);
        assertTrue(DiagnosticLanguage.parse("zh-cn").isPresent());
    }

    @Test
    void keepsTheStableCodeAndRendersAKeyedDiagnosticThroughTheDefaultCatalogue() {
        Diagnostic diagnostic = Diagnostic.localizedError(
            "MPL1001", "compiler.target.unsupported", "v999");

        assertEquals("MPL1001", diagnostic.code());
        assertEquals("不支持的 Mindustry target profile：v999", diagnostic.message());
        assertEquals(diagnostic.message(), diagnostic.render(DiagnosticLanguage.ZH_CN));
    }
}
