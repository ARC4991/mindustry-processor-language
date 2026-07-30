package com.arc.mpl.project;

import com.arc.mpl.memory.PhysicalMemoryLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic positions shared by schematic serialization and deployment guidance. */
public record BlueprintLayout(int width, int height, List<ShardPlacement> shards,
                              List<MemoryPlacement> memories) {
    public BlueprintLayout {
        if (width < 1 || height < 1) throw new IllegalArgumentException("蓝图尺寸必须为正数");
        shards = List.copyOf(Objects.requireNonNull(shards, "shards"));
        memories = List.copyOf(Objects.requireNonNull(memories, "memories"));
        if (shards.isEmpty()) throw new IllegalArgumentException("蓝图必须至少包含一个处理器 shard");
    }

    public static BlueprintLayout singleShard(RuntimePlan plan) {
        return topology(RuntimeTopologyPlan.singleShard(plan));
    }

    public static BlueprintLayout topology(RuntimeTopologyPlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<Integer> processorSizes = plan.shards().stream().map(BlueprintLayout::processorSize).toList();
        List<PhysicalMemoryLayout.Segment> segments = plan.physicalMemoryLayout().segments();
        Packing packing = compactPacking(processorSizes, segments);
        List<ShardPlacement> shards = new ArrayList<>();
        for (int index = 0; index < plan.shards().size(); index++) {
            ShardPlan shard = plan.shards().get(index);
            int size = processorSizes.get(index);
            Rectangle rectangle = packing.processors().get(index);
            shards.add(new ShardPlacement(shard.id(), shard.processorId(), shard.roles(),
                anchor(rectangle.x(), size), anchor(rectangle.y(), size)));
        }
        List<MemoryPlacement> memories = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            PhysicalMemoryLayout.Segment segment = segments.get(index);
            Rectangle rectangle = packing.memories().get(index);
            int size = memorySize(segment);
            memories.add(new MemoryPlacement(segment, anchor(rectangle.x(), size), anchor(rectangle.y(), size)));
        }
        return new BlueprintLayout(packing.width(), packing.height(), shards, memories);
    }

    /** Exhaustively chooses a deterministic minimum-area first-fit packing for processors then Memory. */
    private static Packing compactPacking(List<Integer> processorSizes,
                                           List<PhysicalMemoryLayout.Segment> segments) {
        int minimumWidth = 1;
        int maximumWidth = 0;
        for (int size : processorSizes) {
            minimumWidth = Math.max(minimumWidth, size);
            maximumWidth += size;
        }
        for (PhysicalMemoryLayout.Segment segment : segments) {
            minimumWidth = Math.max(minimumWidth, memorySize(segment));
            maximumWidth += memorySize(segment);
        }
        Packing best = null;
        for (int width = minimumWidth; width <= maximumWidth; width++) {
            Packing candidate = packAtWidth(processorSizes, segments, width, maximumWidth);
            if (best == null || candidate.area() < best.area()
                || candidate.area() == best.area() && candidate.maximumDimension() < best.maximumDimension()
                || candidate.area() == best.area() && candidate.maximumDimension() == best.maximumDimension()
                    && candidate.height() < best.height()) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best, "compact blueprint packing");
    }

    private static Packing packAtWidth(List<Integer> processorSizes,
                                       List<PhysicalMemoryLayout.Segment> segments,
                                       int width, int maximumHeight) {
        boolean[][] occupied = new boolean[maximumHeight][width];
        List<Rectangle> processors = new ArrayList<>();
        int usedWidth = 0;
        int usedHeight = 0;
        for (int size : processorSizes) {
            Rectangle processor = firstFree(occupied, size);
            occupy(occupied, processor);
            processors.add(processor);
            usedWidth = Math.max(usedWidth, processor.x() + size);
            usedHeight = Math.max(usedHeight, processor.y() + size);
        }
        List<Rectangle> memories = new ArrayList<>();
        for (PhysicalMemoryLayout.Segment segment : segments) {
            int size = memorySize(segment);
            Rectangle placed = firstFree(occupied, size);
            occupy(occupied, placed);
            memories.add(placed);
            usedWidth = Math.max(usedWidth, placed.x() + size);
            usedHeight = Math.max(usedHeight, placed.y() + size);
        }
        return new Packing(usedWidth, usedHeight, List.copyOf(processors), List.copyOf(memories));
    }

    private static Rectangle firstFree(boolean[][] occupied, int size) {
        for (int y = 0; y <= occupied.length - size; y++) {
            for (int x = 0; x <= occupied[0].length - size; x++) {
                boolean free = true;
                for (int iy = y; iy < y + size && free; iy++) {
                    for (int ix = x; ix < x + size; ix++) {
                        if (occupied[iy][ix]) { free = false; break; }
                    }
                }
                if (free) return new Rectangle(x, y, size);
            }
        }
        throw new IllegalArgumentException("无法在蓝图候选宽度内放置 Runtime 方块");
    }

    private static void occupy(boolean[][] occupied, Rectangle rectangle) {
        for (int y = rectangle.y(); y < rectangle.y() + rectangle.size(); y++) {
            for (int x = rectangle.x(); x < rectangle.x() + rectangle.size(); x++) occupied[y][x] = true;
        }
    }

    private static int memorySize(PhysicalMemoryLayout.Segment segment) {
        return segment.kind() == RuntimePreferences.MemoryKind.CELL ? 1 : 2;
    }

    private static int processorSize(ShardPlan shard) {
        return switch (shard.processor()) {
            case MICRO -> 1;
            case LOGIC -> 2;
            case HYPER -> 3;
        };
    }

    private static int anchor(int origin, int size) {
        return origin + (size - 1) / 2;
    }

    public ShardPlacement main() {
        return shards.stream().filter(shard -> shard.roles().contains("main")).findFirst().orElseThrow();
    }

    public record ShardPlacement(String id, String processor, List<String> roles, int x, int y) {
        public ShardPlacement {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(processor, "processor");
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            if (x < 0 || y < 0) throw new IllegalArgumentException("处理器蓝图坐标不得为负数");
        }
    }

    public record MemoryPlacement(PhysicalMemoryLayout.Segment segment, int x, int y) {
        public MemoryPlacement {
            Objects.requireNonNull(segment, "segment");
            if (x < 0 || y < 0) throw new IllegalArgumentException("Memory 蓝图坐标不得为负数");
        }
    }

    private record Rectangle(int x, int y, int size) { }

    private record Packing(int width, int height, List<Rectangle> processors, List<Rectangle> memories) {
        private int area() { return Math.multiplyExact(width, height); }
        private int maximumDimension() { return Math.max(width, height); }
    }
}
