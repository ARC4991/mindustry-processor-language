package com.arc.mpl.compiler;

import com.arc.mpl.codegen.MlogCodeGenerator;
import com.arc.mpl.codegen.MlogLabelStyle;
import com.arc.mpl.codegen.MlogOutputValidator;
import com.arc.mpl.codegen.MilCodeGenerator;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.project.HardwareLoader;
import com.arc.mpl.project.HardwareContract;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.semantic.SemanticAnalyzer;
import com.arc.mpl.semantic.SemanticResult;
import com.arc.mpl.syntax.MplSyntaxParser;
import com.arc.mpl.syntax.ParseResult;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.optimization.HirOptimizationResult;
import com.arc.mpl.optimization.HirOptimizer;

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
                List.of(Diagnostic.localizedError(
                    "MPL1001", "compiler.target.unsupported", request.targetProfile())),
                Optional.empty());
        }

        Path sourceFile = request.projectDirectory().resolve("src/main.mpl");
        if (!Files.isRegularFile(sourceFile)) {
            return new CompilationResult(profile, List.of(Diagnostic.localized(
                Severity.ERROR, "MPL1101", "compiler.entry.missing", List.of(sourceFile),
                Optional.of(sourceFile), Optional.empty())), Optional.empty());
        }
        String source;
        try {
            source = Files.readString(sourceFile);
        } catch (IOException exception) {
            return new CompilationResult(profile, List.of(Diagnostic.localized(
                Severity.ERROR, "MPL1102", "compiler.entry.read", List.of(exceptionMessage(exception)),
                Optional.of(sourceFile), Optional.empty())), Optional.empty());
        }

        ParseResult parsed = new MplSyntaxParser().parse(source, sourceFile);
        if (!parsed.succeeded()) return new CompilationResult(profile, parsed.diagnostics(), Optional.empty());
        SemanticResult analyzed;
        try {
            HardwareContract hardware = new HardwareLoader().load(request.projectDirectory());
            List<Diagnostic> hardwareDiagnostics = validateHardware(hardware, profile.orElseThrow(), sourceFile);
            if (!hardwareDiagnostics.isEmpty()) return new CompilationResult(profile, hardwareDiagnostics, Optional.empty());
            analyzed = new SemanticAnalyzer(profile.orElseThrow()).analyze(parsed.program().orElseThrow(), sourceFile, hardware);
        } catch (IOException exception) {
            return new CompilationResult(profile, List.of(Diagnostic.localized(
                Severity.ERROR, "MPL1103", "compiler.hardware.read", List.of(exceptionMessage(exception)),
                Optional.of(sourceFile), Optional.empty())), Optional.empty());
        }
        if (analyzed.program().isEmpty()) return new CompilationResult(profile, analyzed.diagnostics(), Optional.empty());
        HirOptimizationResult optimized = new HirOptimizer().optimize(analyzed.program().orElseThrow());
        HirProgram program = optimized.program();
        MlogLabelStyle labelStyle = request.debug() ? MlogLabelStyle.DEBUG : MlogLabelStyle.RELEASE;
        String mil = new MilCodeGenerator().generate(program);
        String mlog = new MlogCodeGenerator(labelStyle).generate(program);
        List<Diagnostic> diagnostics = new ArrayList<>(analyzed.diagnostics());
        diagnostics.addAll(new MlogOutputValidator().validate(mlog, profile.orElseThrow()));
        boolean hasError = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
        return new CompilationResult(profile, diagnostics,
            hasError ? Optional.empty() : Optional.of(mlog),
            hasError ? Optional.empty() : Optional.of(mil), optimized.report());
    }

    private String exceptionMessage(IOException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private List<Diagnostic> validateHardware(HardwareContract contract, TargetProfile profile, Path sourceFile) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (HardwareContract.LinkDeclaration link : contract.links()) {
            if (profile.buildingType(link.mplType()).isEmpty()) {
                diagnostics.add(Diagnostic.localized(Severity.ERROR, "MPL1201", "compiler.hardware.type.unsupported",
                    List.of(link.mplType(), profile.id()), Optional.of(sourceFile), Optional.empty()));
            }
        }
        return List.copyOf(diagnostics);
    }
}
