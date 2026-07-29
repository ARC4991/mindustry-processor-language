package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.optimization.OptimizationReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Writes a self-contained deployable build directory. */
@Slf4j
public final class BuildArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    public void write(Path directory, String mlog, String mil, TargetProfile profile,
                      HardwareContract hardware, RuntimePlan plan) throws IOException {
        write(directory, mlog, mil, profile, hardware, plan, new ProjectMetadata("mpl-project", "0.0.0"));
    }

    public void write(Path directory, String mlog, String mil, TargetProfile profile,
                      HardwareContract hardware, RuntimePlan plan, ProjectMetadata metadata) throws IOException {
        write(directory, mlog, mil, profile, hardware, plan, metadata, OptimizationReport.NONE);
    }

    public void write(Path directory, String mlog, String mil, TargetProfile profile,
                      HardwareContract hardware, RuntimePlan plan, ProjectMetadata metadata,
                      OptimizationReport optimizationReport) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("Main.mlog"), mlog);
        Files.writeString(directory.resolve("Main.mil"), mil);
        String digest = digest(mlog + "\n" + mil + "\n" + profile.id() + "\n" + metadata.name() + "\n" + metadata.version());
        String blueprintName = "MPL-" + metadata.name() + "-" + metadata.version() + "-" + digest.substring(0, 12);
        Files.write(directory.resolve("runtime.msch"), new MindustrySchematicWriter().write(mlog, plan, blueprintName, digest));
        writeFormattedJson(directory.resolve("report.json"), report(profile, plan, digest, optimizationReport));
        writeFormattedJson(directory.resolve("deployment.json"), deployment(profile, hardware, plan, digest));
        log.info("构建产物已写入：{}（blueprint={}，processor={}，instructions={}）", directory, blueprintName, plan.processorId(), plan.instructions());
    }

    private String report(TargetProfile profile, RuntimePlan plan, String digest, OptimizationReport optimizationReport) {
        String resources = "{\"instructions\":%d,\"labels\":%d,\"virtualSlots\":%d,\"physicalSlots\":%d,\"objectPoolSlots\":0,\"stringSlots\":0,\"runtimeSlots\":0}"
            .formatted(plan.instructions(), plan.labels(), plan.virtualSlots(), plan.physicalSlots());
        String optimizations = "[{\"name\":\"constantFolds\",\"applied\":%d},{\"name\":\"eliminatedBranches\",\"applied\":%d},{\"name\":\"eliminatedLoops\",\"applied\":%d},{\"name\":\"eliminatedStatements\",\"applied\":%d}]"
            .formatted(optimizationReport.constantFolds(), optimizationReport.eliminatedBranches(),
                optimizationReport.eliminatedLoops(), optimizationReport.eliminatedStatements());
        return "{\"schemaVersion\":1,\"compiler\":{\"version\":\"0.1.0-SNAPSHOT\"},\"inputDigest\":\"%s\",\"targetProfile\":\"%s\",\"success\":true,\"diagnosticSummary\":{\"errors\":0,\"warnings\":0},\"totals\":%s,\"shards\":[{\"id\":\"Main\",\"processor\":\"%s\",\"mlog\":\"Main.mlog\",\"ipt\":%d,\"maxTokensPerStatement\":%d,\"resources\":%s}],\"optimizations\":%s}"
            .formatted(digest, profile.id(), resources, plan.processorId(), profile.instructionsPerTick(plan.processor()), plan.maxTokensPerStatement(), resources, optimizations);
    }

    private String deployment(TargetProfile profile, HardwareContract hardware, RuntimePlan plan, String digest) {
        String external = hardware.links().stream().map(link -> "{\"mplName\":\"%s\",\"ownerShard\":\"Main\",\"alias\":\"%s\",\"type\":\"%s\",\"access\":\"%s\"}"
            .formatted(escape(link.mplName()), escape(link.gameAlias()), escape(link.mplType()), access(link.mplType()))).collect(java.util.stream.Collectors.joining(","));
        String blocks = plan.physicalSlots() == 0 ? "[\"processor\"]" : "[\"processor\",\"memory\"]";
        String topology = "{\"blueprint\":{\"file\":\"runtime.msch\",\"blocks\":%s},\"shards\":[{\"id\":\"Main\",\"processor\":\"%s\",\"mlog\":\"Main.mlog\"}],\"memorySegments\":[]}".formatted(blocks, plan.processorId());
        return "{\"schemaVersion\":1,\"compiler\":{\"version\":\"0.1.0-SNAPSHOT\"},\"inputDigest\":\"%s\",\"targetProfile\":\"%s\",\"layoutFingerprint\":\"%s\",\"runtimeTopology\":%s,\"externalHardware\":[%s],\"prerequisites\":[]}"
            .formatted(digest, profile.id(), digest(topology), topology, external);
    }

    private String access(String type) { return ("Message".equals(type) || "Display".equals(type)) ? "write" : "readWrite"; }
    private String escape(String text) { return text.replace("\\", "\\\\").replace("\"", "\\\""); }

    /** Writes stable human-readable JSON so build artifacts remain suitable for review and tooling. */
    private void writeFormattedJson(Path file, String source) throws IOException {
        try {
            Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(source)) + "\n");
        } catch (JsonProcessingException exception) {
            throw new IOException("无法格式化编译产物 JSON：" + file.getFileName(), exception);
        }
    }

    private String digest(String text) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
