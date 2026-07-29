package com.arc.mpl.project;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/** Writes the audited v146 .msch wire format for the compiler runtime topology. */
public final class MindustrySchematicWriter {
    /** Writes the current automatic single-shard topology. */
    public byte[] write(String mlog, RuntimePlan plan, String name, String buildHash) throws IOException {
        return write(mlog, plan, BlueprintLayout.singleShard(plan), name, buildHash);
    }

    byte[] write(String mlog, RuntimePlan plan, BlueprintLayout layout, String name, String buildHash) throws IOException {
        BlueprintLayout.ShardPlacement main = layout.main();
        List<Link> links = layout.memories().stream().map(memory -> new Link(memory.segment().alias(),
            memory.x() - main.x(), memory.y() - main.y())).toList();
        List<Tile> tiles = new java.util.ArrayList<>();
        tiles.add(new Tile(processorBlock(plan), main.x(), main.y(), logicConfig(mlog, links)));
        for (BlueprintLayout.MemoryPlacement memory : layout.memories()) {
            boolean cell = memory.segment().kind() == RuntimePreferences.MemoryKind.CELL;
            tiles.add(new Tile(cell ? "memory-cell" : "memory-bank", memory.x(), memory.y(), null));
        }
        return schematic(tiles, layout.width(), layout.height(), name, buildHash);
    }

    private String processorBlock(RuntimePlan plan) {
        return switch (plan.processor()) {
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
                stream.writeInt(tile.x | tile.y << 16);
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
