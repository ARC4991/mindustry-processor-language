package com.arc.mpl.project;

import java.io.IOException;
import java.nio.file.Path;

/** Project identity used in deployable artifact names. */
public record ProjectMetadata(String name, String version) {
    public static ProjectMetadata load(Path projectDirectory) throws IOException {
        ProjectManifest manifest = new ProjectManifestLoader().load(projectDirectory);
        return new ProjectMetadata(manifest.name(), manifest.version());
    }
}
