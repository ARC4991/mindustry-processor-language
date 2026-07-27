package com.arc.mpl.compiler;

import com.arc.mpl.codegen.MlogCodeGenerator;
import com.arc.mpl.codegen.MlogOutputValidator;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.project.HardwareLoader;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.semantic.SemanticAnalyzer;
import com.arc.mpl.semantic.SemanticResult;
import com.arc.mpl.syntax.MplSyntaxParser;
import com.arc.mpl.syntax.ParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
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

        Path sourceFile = request.projectDirectory().resolve("src/main.mpl");
        if (!Files.isRegularFile(sourceFile)) {
            return new CompilationResult(profile, List.of(new Diagnostic(
                Severity.ERROR, "MPL1101", "找不到项目入口文件：" + sourceFile,
                Optional.of(sourceFile), Optional.empty())), Optional.empty());
        }
        String source;
        try {
            source = Files.readString(sourceFile);
        } catch (IOException exception) {
            return new CompilationResult(profile, List.of(new Diagnostic(
                Severity.ERROR, "MPL1102", "无法读取项目入口文件：" + exception.getMessage(),
                Optional.of(sourceFile), Optional.empty())), Optional.empty());
        }

        ParseResult parsed = new MplSyntaxParser().parse(source, sourceFile);
        if (!parsed.succeeded()) return new CompilationResult(profile, parsed.diagnostics(), Optional.empty());
        SemanticResult analyzed;
        try {
            analyzed = new SemanticAnalyzer().analyze(parsed.program().orElseThrow(), sourceFile,
                new HardwareLoader().loadMessages(request.projectDirectory()));
        } catch (IOException exception) {
            return new CompilationResult(profile, List.of(new Diagnostic(
                Severity.ERROR, "MPL1103", "无法读取硬件声明：" + exception.getMessage(),
                Optional.of(sourceFile), Optional.empty())), Optional.empty());
        }
        if (analyzed.program().isEmpty()) return new CompilationResult(profile, analyzed.diagnostics(), Optional.empty());
        String mlog = new MlogCodeGenerator().generate(analyzed.program().orElseThrow());
        List<Diagnostic> diagnostics = new ArrayList<>(analyzed.diagnostics());
        diagnostics.addAll(new MlogOutputValidator().validate(mlog, profile.orElseThrow()));
        return new CompilationResult(profile, diagnostics,
            diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR)
                ? Optional.empty()
                : Optional.of(mlog));
    }
}
