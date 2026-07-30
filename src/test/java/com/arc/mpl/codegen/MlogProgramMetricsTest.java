package com.arc.mpl.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MlogProgramMetricsTest {
    @Test
    void countsCommentsLabelsAndQuotedHashesLikeTheTargetValidator() {
        MlogProgramMetrics metrics = MlogProgramMetrics.analyze("""
            # build marker
            start: # executable address 0
            print "value # retained"
            set result 1 # trailing comment
            """);

        assertEquals(2, metrics.instructions());
        assertEquals(1, metrics.labels());
        assertEquals(3, metrics.maxTokensPerStatement());
        assertFalse(metrics.lines().stream().anyMatch(MlogProgramMetrics.Line::unterminatedString));
    }
}
