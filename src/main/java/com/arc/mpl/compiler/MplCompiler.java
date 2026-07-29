package com.arc.mpl.compiler;

import com.arc.mpl.ast.Program;
import com.arc.mpl.codegen.MlogCodeGenerator;
import com.arc.mpl.codegen.MlogCodeGenerator.HardwareRequirement;
import com.arc.mpl.codegen.MlogLabelStyle;
import com.arc.mpl.codegen.MlogOutputValidator;
import com.arc.mpl.codegen.MilCodeGenerator;
import com.arc.mpl.diagnostic.Diagnostic;
import com.arc.mpl.diagnostic.Severity;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.memory.PhysicalMemoryPlanner;
import com.arc.mpl.optimization.HirOptimizationResult;
import com.arc.mpl.optimization.HirOptimizer;
import com.arc.mpl.profile.KnownProfiles;
import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.project.HardwareContract;
import com.arc.mpl.project.HardwareLoader;
import com.arc.mpl.project.LockedPackageResolver;
import com.arc.mpl.project.ProjectManifest;
import com.arc.mpl.project.ProjectManifestLoader;
import com.arc.mpl.project.ProjectSourceCatalog;
import com.arc.mpl.project.ProjectSourceLoader;
import com.arc.mpl.project.ProjectProgramLoader;
import com.arc.mpl.project.ProjectProgramResult;
import com.arc.mpl.project.ResolvedPackageGraph;
import com.arc.mpl.project.RuntimePreferences;
import com.arc.mpl.runtime.DisplayRuntimeLowerer;
import com.arc.mpl.semantic.SemanticAnalyzer;
import com.arc.mpl.semantic.SemanticResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
                List.of(Diagnostic.localizedError(
                    "MPL1001", "compiler.target.unsupported", request.targetProfile())),
                Optional.empty());
        }

        ProjectManifest manifest;
        ProjectSourceCatalog sources;
        try {
            manifest = new ProjectManifestLoader().load(request.projectDirectory());
            sources = new ProjectSourceLoader().load(request.projectDirectory(), manifest);
        } catch (IOException | IllegalArgumentException exception) {
            Path metadata = request.projectDirectory().resolve("mpl.json");
            return new CompilationResult(profile, List.of(Diagnostic.localized(
                Severity.ERROR, "MPL1105", "compiler.source.config", List.of(exceptionMessage(exception)),
                Optional.of(metadata), Optional.empty())), Optional.empty());
        }
        ResolvedPackageGraph packages;
        try {
            packages = new LockedPackageResolver().resolve(request.projectDirectory(), manifest, profile.orElseThrow());
        } catch (IOException | IllegalArgumentException exception) {
            return new CompilationResult(profile, List.of(Diagnostic.localized(
                Severity.ERROR, "MPL1401", "compiler.package.resolve", List.of(exceptionMessage(exception)),
                Optional.of(request.projectDirectory().resolve("mpl.lock")), Optional.empty())), Optional.empty());
        }
        Path sourceFile = sources.entryFile();
        if (!Files.isRegularFile(sourceFile)) {
            return new CompilationResult(profile, List.of(Diagnostic.localized(
                Severity.ERROR, "MPL1101", "compiler.entry.missing", List.of(sourceFile),
                Optional.of(sourceFile), Optional.empty())), Optional.empty());
        }
        RuntimePreferences runtimePreferences = manifest.runtime();
        HardwareContract hardware;
        try {
            hardware = new HardwareLoader().load(request.projectDirectory(), manifest);
            List<Diagnostic> hardwareDiagnostics = validateHardware(hardware, profile.orElseThrow(), sourceFile);
            if (!hardwareDiagnostics.isEmpty()) return new CompilationResult(profile, hardwareDiagnostics, Optional.empty());
        } catch (IOException exception) {
            return new CompilationResult(profile, List.of(Diagnostic.localized(
                Severity.ERROR, "MPL1103", "compiler.hardware.read", List.of(exceptionMessage(exception)),
                Optional.of(sourceFile), Optional.empty())), Optional.empty());
        }
        ProjectProgramResult projectProgram = new ProjectProgramLoader().load(
            sources, profile.orElseThrow(), hardware, packages);
        if (!projectProgram.succeeded()) {
            return new CompilationResult(profile, projectProgram.diagnostics(), Optional.empty());
        }
        Program syntaxProgram = projectProgram.program().orElseThrow();
        SemanticResult analyzed = new SemanticAnalyzer(profile.orElseThrow()).analyze(syntaxProgram, sourceFile, hardware);
        if (analyzed.program().isEmpty()) return new CompilationResult(profile, analyzed.diagnostics(), Optional.empty());
        HirOptimizationResult optimized = new HirOptimizer().optimize(analyzed.program().orElseThrow());
        HirProgram program = new DisplayRuntimeLowerer(profile.orElseThrow().maxGraphicsBufferCommands())
            .lower(optimized.program());
        PhysicalMemoryLayout memoryLayout;
        try {
            memoryLayout = new PhysicalMemoryPlanner().plan(program, profile.orElseThrow(), runtimePreferences);
        } catch (IllegalArgumentException exception) {
            return new CompilationResult(profile, List.of(new Diagnostic(
                Severity.ERROR, "MPL1301", exception.getMessage(), Optional.of(sourceFile), Optional.empty())),
                Optional.empty(), Optional.empty(), optimized.report(), PhysicalMemoryLayout.empty());
        }
        MlogLabelStyle labelStyle = request.debug() ? MlogLabelStyle.DEBUG : MlogLabelStyle.RELEASE;
        String mil = new MilCodeGenerator().generate(program);
        List<HardwareRequirement> hardwareRequirements = hardware.links().stream()
            .map(link -> new HardwareRequirement(link.gameAlias(), profile.orElseThrow()
                .buildingType(link.mplType()).orElseThrow().mlogName()))
            .toList();
        String mlog = new MlogCodeGenerator(labelStyle, memoryLayout, hardwareRequirements).generate(program);
        List<Diagnostic> diagnostics = new ArrayList<>(analyzed.diagnostics());
        diagnostics.addAll(new MlogOutputValidator().validate(mlog, profile.orElseThrow()));
        boolean hasError = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
        return new CompilationResult(profile, diagnostics,
            hasError ? Optional.empty() : Optional.of(mlog),
            hasError ? Optional.empty() : Optional.of(mil), optimized.report(), memoryLayout);
    }

    private String exceptionMessage(Exception exception) {
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
