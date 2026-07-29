package com.arc.mpl.codegen;

import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlogOutputValidatorTest {
    private final TargetProfile v146 = KnownProfiles.find("v146").orElseThrow();
    private final MlogOutputValidator validator = new MlogOutputValidator();

    @Test
    void acceptsQuotedStringsAsOneMlogToken() {
        List<Diagnostic> diagnostics = validator.validate("print \"a message with spaces\"\nstop\n", v146);

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void rejectsInstructionCountAboveTheTargetLimit() {
        StringBuilder mlog = new StringBuilder();
        for (int index = 0; index < v146.maxInstructions() + 1; index++) {
            mlog.append("set value ").append(index).append('\n');
        }

        List<Diagnostic> diagnostics = validator.validate(mlog.toString(), v146);

        assertEquals(List.of("MPL5001"), diagnostics.stream().map(Diagnostic::code).toList());
    }

    @Test
    void excludesLabelsFromTheInstructionLimit() {
        StringBuilder mlog = new StringBuilder("entry:\n");
        for (int index = 0; index < v146.maxInstructions(); index++) {
            mlog.append("set value 0\n");
        }

        List<Diagnostic> diagnostics = validator.validate(mlog.toString(), v146);

        assertFalse(diagnostics.stream().anyMatch(diagnostic -> diagnostic.code().equals("MPL5001")));
    }

    @Test
    void rejectsStatementsWithTooManyTokens() {
        List<Diagnostic> diagnostics = validator.validate(
            "op add result one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen\n", v146);

        assertEquals(List.of("MPL5003"), diagnostics.stream().map(Diagnostic::code).toList());
    }
}
