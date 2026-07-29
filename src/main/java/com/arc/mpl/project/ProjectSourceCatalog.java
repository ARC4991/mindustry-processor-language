package com.arc.mpl.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Deterministic source discovery result with one configured executable entry. */
public record ProjectSourceCatalog(
    Path sourceRoot,
    Path entryFile,
    ProjectSourceLanguage entryLanguage,
    List<Path> sourceFiles
) {
    public ProjectSourceCatalog {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(entryFile, "entryFile");
        Objects.requireNonNull(entryLanguage, "entryLanguage");
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
    }
}
