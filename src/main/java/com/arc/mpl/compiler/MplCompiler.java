package com.arc.mpl.compiler;

import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;

import java.util.List;
import java.util.Optional;

/**
 * Pipeline facade. Parsing and lowering are deliberately added behind this stable boundary.
 */
public final class MplCompiler {
    public CompilationResult compile(CompilationRequest request) {
        Optional<TargetProfile> profile = KnownProfiles.find(request.targetProfile());
        if (profile.isEmpty()) {
            return new CompilationResult(
                Optional.empty(),
                List.of(Diagnostic.error("MPL1001", "不支持的 Mindustry target profile：" + request.targetProfile())),
                Optional.empty());
        }

        return new CompilationResult(
            profile,
            List.of(Diagnostic.error("MPL9001", "前端尚未实现：无法编译项目 " + request.projectDirectory())),
            Optional.empty());
    }
}
