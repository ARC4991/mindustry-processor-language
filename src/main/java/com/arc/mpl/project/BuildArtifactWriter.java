package com.arc.mpl.project;

import com.arc.mpl.memory.SharedRuntimeLayout;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        write(directory, List.of(new ShardArtifact("Main", mlog, mil)), profile, hardware,
            RuntimeTopologyPlan.singleShard(plan), metadata, optimizationReport);
    }

    public void write(Path directory, List<ShardArtifact> artifacts, TargetProfile profile,
                      HardwareContract hardware, RuntimeTopologyPlan plan, ProjectMetadata metadata,
                      OptimizationReport optimizationReport) throws IOException {
        Objects.requireNonNull(artifacts, "artifacts");
        Objects.requireNonNull(plan, "plan");
        Map<String, ShardArtifact> artifactsById = new LinkedHashMap<>();
        for (ShardArtifact artifact : artifacts) {
            if (artifactsById.putIfAbsent(artifact.id(), artifact) != null) {
                throw new IllegalArgumentException("重复的 shard 构建产物：" + artifact.id());
            }
        }
        List<String> plannedIds = plan.shards().stream().map(ShardPlan::id).toList();
        if (!artifactsById.keySet().equals(new java.util.LinkedHashSet<>(plannedIds))) {
            throw new IllegalArgumentException("shard 构建产物与 Runtime 拓扑不一致");
        }
        Files.createDirectories(directory);
        StringBuilder digestInput = new StringBuilder();
        for (String id : plannedIds) {
            ShardArtifact artifact = artifactsById.get(id);
            digestInput.append(id).append('\n').append(artifact.mlog()).append('\n')
                .append(artifact.mil()).append('\n');
        }
        digestInput.append(profile.id()).append('\n').append(metadata.name()).append('\n').append(metadata.version());
        String digest = digest(digestInput.toString());
        String blueprintName = "MPL-" + metadata.name() + "-" + metadata.version() + "-" + digest.substring(0, 12);
        BlueprintLayout layout = BlueprintLayout.topology(plan);
        Map<String, String> identifiedMlog = new LinkedHashMap<>();
        for (String id : plannedIds) {
            ShardArtifact artifact = artifactsById.get(id);
            String code = "# MPL shard: " + id + " / build: " + digest.substring(0, 12) + "\n" + artifact.mlog();
            identifiedMlog.put(id, code);
            Files.writeString(directory.resolve(id + ".mlog"), code);
            Files.writeString(directory.resolve(id + ".mil"), artifact.mil());
        }
        Files.write(directory.resolve("runtime.msch"),
            new MindustrySchematicWriter().write(identifiedMlog, plan, layout, blueprintName, digest));
        writeFormattedJson(directory.resolve("report.json"), report(profile, plan, digest, optimizationReport));
        writeFormattedJson(directory.resolve("deployment.json"), deployment(profile, hardware, plan, layout, digest));
        Files.writeString(directory.resolve("连接说明.txt"), connectionGuide(hardware, layout, blueprintName, digest));
        log.info("构建产物已写入：{}（blueprint={}，shards={}，instructions={}）",
            directory, blueprintName, plan.shards().size(), plan.instructions());
    }

    private ObjectNode report(TargetProfile profile, RuntimeTopologyPlan plan, String digest,
                              OptimizationReport optimizationReport) {
        ObjectNode report = artifactHeader(profile, digest);
        report.put("success", true);
        report.putObject("diagnosticSummary").put("errors", 0).put("warnings", 0);
        report.set("totals", totalResources(plan));

        ArrayNode shards = report.putArray("shards");
        for (ShardPlan planned : plan.shards()) {
            ObjectNode shard = shards.addObject();
            shard.put("id", planned.id());
            shard.put("processor", planned.processorId());
            shard.put("mlog", planned.id() + ".mlog");
            shard.put("ipt", profile.instructionsPerTick(planned.processor()));
            shard.put("maxTokensPerStatement", planned.maxTokensPerStatement());
            shard.set("resources", shardResources(planned, plan));
        }

        ArrayNode optimizations = report.putArray("optimizations");
        addOptimization(optimizations, "constantFolds", optimizationReport.constantFolds());
        addOptimization(optimizations, "eliminatedBranches", optimizationReport.eliminatedBranches());
        addOptimization(optimizations, "eliminatedLoops", optimizationReport.eliminatedLoops());
        addOptimization(optimizations, "eliminatedStatements", optimizationReport.eliminatedStatements());
        optimizationReport.profileOptimizations().forEach(optimization -> {
            ObjectNode node = addOptimization(optimizations, optimization.name(), optimization.applied());
            node.put("estimatedInstructionsSaved", optimization.estimatedInstructionsSaved());
            node.put("estimatedLabelsSaved", optimization.estimatedLabelsSaved());
        });
        return report;
    }

    private ObjectNode deployment(TargetProfile profile, HardwareContract hardware, RuntimeTopologyPlan plan,
                                  BlueprintLayout layout, String digest) {
        ObjectNode topology = JSON.createObjectNode();
        ArrayNode blocks = topology.putObject("blueprint").put("file", "runtime.msch").putArray("blocks");
        blocks.add("processor");
        if (!plan.physicalMemoryLayout().segments().isEmpty()) blocks.add("memory");

        ArrayNode shards = topology.putArray("shards");
        for (BlueprintLayout.ShardPlacement placement : layout.shards()) {
            ObjectNode shard = shards.addObject();
            shard.put("id", placement.id());
            shard.put("processor", placement.processor());
            shard.put("mlog", placement.id() + ".mlog");
            ArrayNode roles = shard.putArray("roles");
            placement.roles().forEach(roles::add);
            shard.putObject("blueprintPosition").put("x", placement.x()).put("y", placement.y());
        }

        ArrayNode memorySegments = topology.putArray("memorySegments");
        plan.physicalMemoryLayout().segments().forEach(segment -> {
            BlueprintLayout.MemoryPlacement placement = layout.memories().stream()
                .filter(memory -> memory.segment().alias().equals(segment.alias())).findFirst()
                .orElseThrow(() -> new IllegalStateException("蓝图缺少 Memory 布局：" + segment.alias()));
            ObjectNode memory = memorySegments.addObject();
            memory.put("id", segment.alias());
            memory.put("kind", segment.kind().name().toLowerCase(java.util.Locale.ROOT));
            memory.put("capacity", segment.capacity());
            memory.put("usedSlots", segment.usedSlots());
            memory.putObject("blueprintPosition").put("x", placement.x()).put("y", placement.y());
            ArrayNode bindings = memory.putArray("bindings");
            plan.shards().forEach(planned -> {
                ObjectNode binding = bindings.addObject();
                binding.put("shard", planned.id());
                binding.put("alias", segment.alias());
                binding.put("access", "readWrite");
                binding.put("autoConnected", true);
            });
        });
        plan.sharedRuntime().ifPresent(shared -> {
            ObjectNode runtime = topology.putObject("sharedRuntime");
            runtime.put("magic", SharedRuntimeLayout.MAGIC);
            runtime.put("abiVersion", SharedRuntimeLayout.ABI_VERSION);
            runtime.put("fingerprint", shared.fingerprint());
            runtime.put("epoch", shared.epoch());
            runtime.put("mainShard", shared.mainShard());
            ArrayNode workers = runtime.putArray("workers");
            shared.workers().forEach(workers::add);
            runtime.put("slots", shared.slots());
            runtime.put("readyIndex", SharedRuntimeLayout.READY_INDEX);
            ObjectNode acknowledgements = runtime.putObject("acknowledgementIndexes");
            shared.workers().forEach(worker -> acknowledgements.put(worker, shared.acknowledgementIndex(worker)));
            ObjectNode heartbeats = runtime.putObject("heartbeatIndexes");
            shared.workers().forEach(worker -> heartbeats.put(worker, shared.heartbeatIndex(worker)));
            ArrayNode slices = runtime.putArray("slices");
            shared.header().slices().forEach(slice -> {
                ObjectNode item = slices.addObject();
                item.put("memory", plan.physicalMemoryLayout().segments().get(slice.segmentIndex()).alias());
                item.put("offset", slice.offset());
                item.put("logicalStart", slice.logicalStart());
                item.put("length", slice.length());
            });
            ArrayNode mailboxes = runtime.putArray("mailboxes");
            shared.mailboxes().forEach(mailbox -> {
                ObjectNode item = mailboxes.addObject();
                item.put("id", mailbox.id());
                item.put("producer", mailbox.producer());
                item.put("consumer", mailbox.consumer());
                item.put("payloadSlots", mailbox.payloadSlots());
                item.put("slots", mailbox.slots());
                ArrayNode mailboxSlices = item.putArray("slices");
                mailbox.allocation().slices().forEach(slice -> {
                    ObjectNode part = mailboxSlices.addObject();
                    part.put("memory", plan.physicalMemoryLayout().segments().get(slice.segmentIndex()).alias());
                    part.put("offset", slice.offset());
                    part.put("logicalStart", slice.logicalStart());
                    part.put("length", slice.length());
                });
            });
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
            if (link.width() > 0) {
                external.put("width", link.width());
                external.put("height", link.height());
            }
        });
        ArrayNode logicalDisplays = deployment.putArray("logicalDisplays");
        hardware.resources().values().stream()
            .filter(resource -> "Display".equals(resource.mplType()) && resource.display().isPresent())
            .forEach(resource -> {
                HardwareContract.DisplayLayout displayLayout = resource.display().orElseThrow();
                ObjectNode display = logicalDisplays.addObject();
                display.put("mplName", resource.mplName());
                display.put("width", displayLayout.width());
                display.put("height", displayLayout.height());
                ArrayNode tiles = display.putArray("tiles");
                displayLayout.tiles().forEach(tile -> {
                    ObjectNode member = tiles.addObject();
                    member.put("mplName", tile.mplName());
                    member.put("alias", tile.gameAlias());
                    member.put("x", tile.x());
                    member.put("y", tile.y());
                    member.put("width", tile.width());
                    member.put("height", tile.height());
                });
            });
        deployment.putArray("prerequisites");
        return deployment;
    }

    private String connectionGuide(HardwareContract hardware, BlueprintLayout layout, String blueprintName, String digest) {
        BlueprintLayout.ShardPlacement main = layout.main();
        StringBuilder guide = new StringBuilder();
        guide.append("MPL 外部硬件连接说明\n");
        guide.append("蓝图：").append(blueprintName).append('\n');
        guide.append("构建：").append(digest).append("\n\n");
        guide.append("所有外部硬件只连接 Main。");
        if (layout.shards().size() == 1) guide.append("Main 是蓝图最左侧处理器，");
        guide.append("Main 局部坐标为 (").append(main.x()).append(", ").append(main.y()).append(")。\n");
        guide.append("打开处理器代码时，首行应为：# MPL shard: Main / build: ")
            .append(digest, 0, 12).append("\n\n");
        if (layout.shards().size() > 1) {
            guide.append("蓝图处理器位置与角色：\n");
            for (BlueprintLayout.ShardPlacement shard : layout.shards()) {
                guide.append("- ").append(shard.id()).append(" @ (").append(shard.x()).append(", ")
                    .append(shard.y()).append(") roles=").append(shard.roles()).append('\n');
            }
            guide.append('\n');
        }
        if (!layout.memories().isEmpty()) {
            if (layout.shards().size() == 1) {
                guide.append("蓝图内的 Runtime Memory 已自动连接到 Main，无需手动重新连接。内部 alias：");
            } else {
                guide.append("蓝图内的 Runtime Memory 已自动连接到所有 shard，无需手动重新连接。处理器：")
                    .append(layout.shards().stream().map(BlueprintLayout.ShardPlacement::id).toList())
                    .append("；内部 alias：");
            }
            guide.append(layout.memories().stream().map(memory -> memory.segment().alias()).toList())
                .append("。\n\n");
        }
        if (hardware.links().isEmpty()) {
            guide.append("本项目不需要手动连接外部硬件。\n");
            return guide.toString();
        }
        guide.append("按以下 alias 连接；多个同类建筑按数字从小到大连接：\n");
        for (HardwareContract.LinkDeclaration link : hardware.links()) {
            guide.append("- ").append(link.mplName()).append(" : ").append(link.mplType())
                .append(" -> ").append(link.gameAlias());
            if (link.width() > 0) guide.append(" (").append(link.width()).append('x').append(link.height()).append(')');
            guide.append('\n');
        }
        List<HardwareContract.Resource> composed = hardware.resources().values().stream()
            .filter(resource -> resource.display().isPresent() && !resource.directlyLinked()).toList();
        if (!composed.isEmpty()) {
            guide.append("\n逻辑组合屏：\n");
            for (HardwareContract.Resource resource : composed) {
                HardwareContract.DisplayLayout display = resource.display().orElseThrow();
                guide.append("- ").append(resource.mplName()).append(" : ")
                    .append(display.width()).append('x').append(display.height()).append(" -> ")
                    .append(display.tiles().stream().map(HardwareContract.DisplayTile::mplName).toList()).append('\n');
            }
        }
        guide.append("\n选中 Main 进入配置模式后，游戏会在已连接建筑上显示实际 alias；必须与上表一致。\n");
        guide.append("Main 会等待全部 alias 存在且类型正确后再进入顶层程序；连接完成后无需重新写入代码。\n");
        return guide.toString();
    }

    private ObjectNode artifactHeader(TargetProfile profile, String digest) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.putObject("compiler").put("version", COMPILER_VERSION);
        root.put("inputDigest", digest);
        root.put("targetProfile", profile.id());
        return root;
    }

    private ObjectNode totalResources(RuntimeTopologyPlan plan) {
        ObjectNode resources = JSON.createObjectNode();
        resources.put("instructions", plan.instructions());
        resources.put("labels", plan.labels());
        resources.put("virtualSlots", plan.virtualSlots());
        resources.put("physicalSlots", plan.physicalSlots());
        resources.put("objectPoolSlots", plan.objectPoolSlots());
        resources.put("stringSlots", plan.stringSlots());
        resources.put("runtimeSlots", plan.runtimeSlots());
        return resources;
    }

    private ObjectNode shardResources(ShardPlan shard, RuntimeTopologyPlan plan) {
        ObjectNode resources = JSON.createObjectNode();
        resources.put("instructions", shard.instructions());
        resources.put("labels", shard.labels());
        resources.put("virtualSlots", shard.virtualSlots());
        boolean ownsSharedLayout = shard.roles().contains("main");
        resources.put("physicalSlots", ownsSharedLayout ? plan.physicalSlots() : 0);
        resources.put("objectPoolSlots", ownsSharedLayout ? plan.objectPoolSlots() : 0);
        resources.put("stringSlots", ownsSharedLayout ? plan.stringSlots() : 0);
        resources.put("runtimeSlots", ownsSharedLayout ? plan.runtimeSlots() : 0);
        return resources;
    }

    private ObjectNode addOptimization(ArrayNode optimizations, String name, int applied) {
        ObjectNode optimization = optimizations.addObject();
        optimization.put("name", name);
        optimization.put("shard", "Main");
        optimization.put("applied", applied);
        return optimization;
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

    public record ShardArtifact(String id, String mlog, String mil) {
        public ShardArtifact {
            if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_-]*")) {
                throw new IllegalArgumentException("无效的 shard 构建产物 id：" + id);
            }
            Objects.requireNonNull(mlog, "mlog");
            Objects.requireNonNull(mil, "mil");
        }
    }
}
