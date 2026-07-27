package com.arc.mpl.compiler;

import java.nio.file.Path;
import java.util.Objects;

/** Inputs owned by the project loader before parsing begins. */
public record CompilationRequest(Path projectDirectory, String targetProfile, boolean debug) {
    public CompilationRequest {
        Objects.requireNonNull(projectDirectory, "projectDirectory");
        Objects.requireNonNull(targetProfile, "targetProfile");
    }

    /** Creates a normal deployment build with compact, release-style labels. */
    public CompilationRequest(Path projectDirectory, String targetProfile) {
        this(projectDirectory, targetProfile, false);
    }
}
