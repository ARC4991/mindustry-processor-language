package com.arc.mpl.optimization;

import com.arc.mpl.codegen.MlogCodeGenerator;
import com.arc.mpl.codegen.MlogCodeGenerator.HardwareRequirement;
import com.arc.mpl.codegen.MlogLabelStyle;
import com.arc.mpl.codegen.MlogProgramMetrics;
import com.arc.mpl.hir.HirProgram;
import com.arc.mpl.memory.PhysicalMemoryLayout;
import com.arc.mpl.profile.TargetProfile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Measures profile-specific code generation against an equivalent baseline lowering. */
public final class ProfileLoweringAnalyzer {
    public OptimizationReport analyze(OptimizationReport report, HirProgram program,
                                      MlogLabelStyle labelStyle, PhysicalMemoryLayout memoryLayout,
                                      List<HardwareRequirement> hardwareRequirements,
                                      TargetProfile profile, String mlog) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(labelStyle, "labelStyle");
        Objects.requireNonNull(memoryLayout, "memoryLayout");
        Objects.requireNonNull(hardwareRequirements, "hardwareRequirements");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(mlog, "mlog");
        if (!profile.capabilities().contains("printchar")) return report;

        Set<String> baselineCapabilities = new LinkedHashSet<>(profile.capabilities());
        baselineCapabilities.remove("printchar");
        String baseline = new MlogCodeGenerator(labelStyle, memoryLayout, hardwareRequirements,
            baselineCapabilities).generate(program);
        MlogProgramMetrics actualMetrics = MlogProgramMetrics.analyze(mlog);
        MlogProgramMetrics baselineMetrics = MlogProgramMetrics.analyze(baseline);
        int applied = Math.toIntExact(mlog.lines().map(String::strip)
            .filter(line -> line.startsWith("printchar ")).count());
        int instructionSavings = Math.max(0, baselineMetrics.instructions() - actualMetrics.instructions());
        int labelSavings = Math.max(0, baselineMetrics.labels() - actualMetrics.labels());
        if (applied == 0 && instructionSavings == 0 && labelSavings == 0) return report;
        return report.withProfileOptimization(new ProfileOptimization(
            "printcharStringOutput", applied, instructionSavings, labelSavings));
    }
}
