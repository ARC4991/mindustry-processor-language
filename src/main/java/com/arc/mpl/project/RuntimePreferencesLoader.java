package com.arc.mpl.project;

import java.io.IOException;
import java.nio.file.Path;

/** Loads the optional runtime policy from mpl.json without exposing physical layout to source code. */
public final class RuntimePreferencesLoader {
    public RuntimePreferences load(Path projectDirectory) throws IOException {
        return new ProjectManifestLoader().load(projectDirectory).runtime();
    }
}
