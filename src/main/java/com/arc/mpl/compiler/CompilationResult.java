package com.arc.mpl.compiler;

import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.profile.TargetProfile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result boundary between the compiler pipeline and CLI/UI clients. */
public record CompilationResult(
    Optional<TargetProfile> profile,
    List<Diagnostic> diagnostics,
    Optional<String> mlog
) {
    public CompilationResult {
        profile = profile == null ? Optional.empty() : profile;
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        mlog = mlog == null ? Optional.empty() : mlog;
    }

    public boolean succeeded() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }
}
