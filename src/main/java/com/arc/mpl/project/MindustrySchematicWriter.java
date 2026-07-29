package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/** Writes the audited v146 .msch wire format for the compiler runtime topology. */
public final class MindustrySchematicWriter {
    /** Writes the current automatic single-shard topology. */
    public byte[] write(String mlog, RuntimePlan plan, String name, String buildHash) throws IOException {
        List<Tile> tiles = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        String processor = switch (plan.processor()) {
            case MICRO -> "micro-processor";
            case LOGIC -> "logic-processor";
            case HYPER -> "hyper-processor";
        };
        tiles.add(new Tile(processor, 1, 1, null));
        int x = 4;
        for (PhysicalMemoryLayout.Segment segment : plan.physicalMemoryLayout().segments()) {
            boolean cell = segment.kind() == RuntimePreferences.MemoryKind.CELL;
            tiles.add(new Tile(cell ? "memory-cell" : "memory-bank", x, 1, null));
            links.add(new Link(segment.alias(), x, 1));
            x += cell ? 2 : 3;
        }
        tiles.set(0, new Tile(processor, 1, 1, logicConfig(mlog, links)));
        return schematic(tiles, Math.max(3, x), 3, name, buildHash);
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
