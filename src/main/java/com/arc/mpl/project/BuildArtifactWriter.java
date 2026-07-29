package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;
import com.arc.mpl.optimization.OptimizationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private static final String COMPILER_VERSION = "0.1.0-SNAPSHOT";

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

    private ObjectNode report(TargetProfile profile, RuntimePlan plan, String digest,
                              OptimizationReport optimizationReport) {
        ObjectNode report = artifactHeader(profile, digest);
        report.put("success", true);
        report.putObject("diagnosticSummary").put("errors", 0).put("warnings", 0);
        report.set("totals", resources(plan));

        ObjectNode shard = report.putArray("shards").addObject();
        shard.put("id", "Main");
        shard.put("processor", plan.processorId());
        shard.put("mlog", "Main.mlog");
        shard.put("ipt", profile.instructionsPerTick(plan.processor()));
        shard.put("maxTokensPerStatement", plan.maxTokensPerStatement());
        shard.set("resources", resources(plan));

        ArrayNode optimizations = report.putArray("optimizations");
        addOptimization(optimizations, "constantFolds", optimizationReport.constantFolds());
        addOptimization(optimizations, "eliminatedBranches", optimizationReport.eliminatedBranches());
        addOptimization(optimizations, "eliminatedLoops", optimizationReport.eliminatedLoops());
        addOptimization(optimizations, "eliminatedStatements", optimizationReport.eliminatedStatements());
        return report;
    }

    private ObjectNode deployment(TargetProfile profile, HardwareContract hardware, RuntimePlan plan, String digest) {
        ObjectNode topology = JSON.createObjectNode();
        ArrayNode blocks = topology.putObject("blueprint").put("file", "runtime.msch").putArray("blocks");
        blocks.add("processor");
        if (!plan.physicalMemoryLayout().segments().isEmpty()) blocks.add("memory");

        ObjectNode shard = topology.putArray("shards").addObject();
        shard.put("id", "Main");
        shard.put("processor", plan.processorId());
        shard.put("mlog", "Main.mlog");

        ArrayNode memorySegments = topology.putArray("memorySegments");
        plan.physicalMemoryLayout().segments().forEach(segment -> {
            ObjectNode memory = memorySegments.addObject();
            memory.put("id", segment.alias());
            memory.put("kind", segment.kind().name().toLowerCase(java.util.Locale.ROOT));
            memory.put("capacity", segment.capacity());
            memory.put("usedSlots", segment.usedSlots());
            ObjectNode binding = memory.putArray("bindings").addObject();
            binding.put("shard", "Main");
            binding.put("alias", segment.alias());
            binding.put("access", "readWrite");
        });

        ObjectNode deployment = artifactHeader(profile, digest);
        deployment.put("layoutFingerprint", digest(topology.toString()));
        deployment.set("runtimeTopology", topology);
        ArrayNode externalHardware = deployment.putArray("externalHardware");
        hardware.links().forEach(link -> {
            ObjectNode external = externalHardware.addObject();
            external.put("mplName", link.mplName());
            external.put("ownerShard", "Main");
            external.put("alias", link.gameAlias());
            external.put("type", link.mplType());
            external.put("access", access(link.mplType()));
        });
        deployment.putArray("prerequisites");
        return deployment;
    }

    private ObjectNode artifactHeader(TargetProfile profile, String digest) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.putObject("compiler").put("version", COMPILER_VERSION);
        root.put("inputDigest", digest);
        root.put("targetProfile", profile.id());
        return root;
    }

    private ObjectNode resources(RuntimePlan plan) {
        ObjectNode resources = JSON.createObjectNode();
        resources.put("instructions", plan.instructions());
        resources.put("labels", plan.labels());
        resources.put("virtualSlots", plan.virtualSlots());
        resources.put("physicalSlots", plan.physicalSlots());
        resources.put("objectPoolSlots", 0);
        resources.put("stringSlots", 0);
        resources.put("runtimeSlots", 0);
        return resources;
    }

    private void addOptimization(ArrayNode optimizations, String name, int applied) {
        ObjectNode optimization = optimizations.addObject();
        optimization.put("name", name);
        optimization.put("shard", "Main");
        optimization.put("applied", applied);
    }

    private String access(String type) { return ("Message".equals(type) || "Display".equals(type)) ? "write" : "readWrite"; }

    /** Writes stable human-readable JSON so build artifacts remain suitable for review and tooling. */
    private void writeFormattedJson(Path file, JsonNode source) throws IOException {
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(source) + "\n");
    }

    private String digest(String text) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
