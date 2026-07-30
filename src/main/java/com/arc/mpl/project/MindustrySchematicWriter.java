package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;

/** Writes the audited v146 .msch wire format for the compiler runtime topology. */
public final class MindustrySchematicWriter {
    /** Writes the current automatic single-shard topology. */
    public byte[] write(String mlog, RuntimePlan plan, String name, String buildHash) throws IOException {
        return write(mlog, plan, BlueprintLayout.singleShard(plan), name, buildHash);
    }

    byte[] write(String mlog, RuntimePlan plan, BlueprintLayout layout, String name, String buildHash) throws IOException {
        return write(Map.of("Main", mlog), RuntimeTopologyPlan.singleShard(plan), layout, name, buildHash);
    }

    public byte[] write(Map<String, String> mlogByShard, RuntimeTopologyPlan plan,
                        String name, String buildHash) throws IOException {
        return write(mlogByShard, plan, BlueprintLayout.topology(plan), name, buildHash);
    }

    byte[] write(Map<String, String> mlogByShard, RuntimeTopologyPlan plan, BlueprintLayout layout,
                 String name, String buildHash) throws IOException {
        Objects.requireNonNull(mlogByShard, "mlogByShard");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(layout, "layout");
        if (plan.shards().size() > 1 && plan.sharedRuntime().isEmpty()) {
            throw new IllegalArgumentException("多处理器蓝图缺少共享 Runtime 启动协议");
        }
        if (!mlogByShard.keySet().equals(plan.shards().stream()
            .map(ShardPlan::id).collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("mlog shard 集合与 Runtime 拓扑不一致");
        }
        validateStableMemoryAliases(plan);
        List<Tile> tiles = new java.util.ArrayList<>();
        for (ShardPlan shard : plan.shards()) {
            BlueprintLayout.ShardPlacement placement = layout.shards().stream()
                .filter(value -> value.id().equals(shard.id())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("蓝图缺少 shard 布局：" + shard.id()));
            List<Link> links = layout.memories().stream().map(memory -> new Link(memory.segment().alias(),
                memory.x() - placement.x(), memory.y() - placement.y())).toList();
            tiles.add(new Tile(processorBlock(shard), placement.x(), placement.y(),
                logicConfig(Objects.requireNonNull(mlogByShard.get(shard.id()), shard.id()), links)));
        }
        for (BlueprintLayout.MemoryPlacement memory : layout.memories()) {
            boolean cell = memory.segment().kind() == RuntimePreferences.MemoryKind.CELL;
            tiles.add(new Tile(cell ? "memory-cell" : "memory-bank", memory.x(), memory.y(), null));
        }
        return schematic(tiles, layout.width(), layout.height(), name, buildHash);
    }

    private void validateStableMemoryAliases(RuntimeTopologyPlan plan) {
        Map<RuntimePreferences.MemoryKind, Integer> ordinals =
            new java.util.EnumMap<>(RuntimePreferences.MemoryKind.class);
        for (PhysicalMemoryLayout.Segment segment : plan.physicalMemoryLayout().segments()) {
            String expected = PhysicalMemoryLayout.automaticLinkAlias(segment.kind(),
                ordinals.merge(segment.kind(), 1, Integer::sum));
            if (!segment.alias().equals(expected)) {
                throw new IllegalArgumentException("蓝图内部 Memory alias 必须为 " + expected + "，实际为 "
                    + segment.alias() + "；否则处理器先于 Memory 完成时，Mindustry 会重命名链接并使 mlog 失去访问");
            }
        }
    }

    private String processorBlock(ShardPlan shard) {
        return switch (shard.processor()) {
            case MICRO -> "micro-processor";
            case LOGIC -> "logic-processor";
            case HYPER -> "hyper-processor";
        };
    }

    private byte[] schematic(List<Tile> tiles, int width, int height, String name, String buildHash) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[]{'m', 's', 'c', 'h', 1});
        try (DataOutputStream stream = new DataOutputStream(new DeflaterOutputStream(output))) {
            stream.writeShort(width); stream.writeShort(height);
            stream.writeByte(3); stream.writeUTF("name"); stream.writeUTF(name);
            stream.writeUTF("labels"); stream.writeUTF("[]");
            stream.writeUTF("mpl.buildHash"); stream.writeUTF(buildHash);
            List<String> blocks = tiles.stream().map(Tile::block).distinct().toList();
            stream.writeByte(blocks.size());
            for (String block : blocks) stream.writeUTF(block);
            stream.writeInt(tiles.size());
            for (Tile tile : tiles) {
                stream.writeByte(blocks.indexOf(tile.block));
                // arc.math.geom.Point2.pack stores x in the high 16 bits and y in the low 16 bits.
                stream.writeInt(((tile.x & 0xffff) << 16) | (tile.y & 0xffff));
                if (tile.config == null) stream.writeByte(0);
                else { stream.writeByte(14); stream.writeInt(tile.config.length); stream.write(tile.config); }
                stream.writeByte(0);
            }
        }
        return output.toByteArray();
    }

    private byte[] logicConfig(String mlog, List<Link> links) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(new DeflaterOutputStream(output))) {
            byte[] code = mlog.getBytes(StandardCharsets.UTF_8);
            stream.writeByte(1); stream.writeInt(code.length); stream.write(code);
            stream.writeInt(links.size());
            for (Link link : links) { stream.writeUTF(link.name); stream.writeShort(link.x); stream.writeShort(link.y); }
        }
        return output.toByteArray();
    }
    private record Tile(String block, int x, int y, byte[] config) { }
    private record Link(String name, int x, int y) { }
}
