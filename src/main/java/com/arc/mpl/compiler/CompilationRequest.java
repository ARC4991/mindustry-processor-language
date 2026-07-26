package com.arc.mpl.compiler;

import java.nio.file.Path;
import java.util.Objects;

/** Inputs owned by the project loader before parsing begins. */
public record CompilationRequest(Path projectDirectory, String targetProfile) {
    public CompilationRequest {
        Objects.requireNonNull(projectDirectory, "projectDirectory");
        Objects.requireNonNull(targetProfile, "targetProfile");
    }
}
