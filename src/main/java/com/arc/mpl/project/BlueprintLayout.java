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
        int processorSize = switch (plan.processor()) {
            case MICRO -> 1;
            case LOGIC -> 2;
            case HYPER -> 3;
        };
        List<PhysicalMemoryLayout.Segment> segments = plan.physicalMemoryLayout().segments();
        Packing packing = compactPacking(processorSize, segments);
        ShardPlacement main = new ShardPlacement("Main", plan.processorId(), List.of("main"),
            anchor(packing.processor().x(), processorSize), anchor(packing.processor().y(), processorSize));
        List<MemoryPlacement> memories = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            PhysicalMemoryLayout.Segment segment = segments.get(index);
            Rectangle rectangle = packing.memories().get(index);
            int size = memorySize(segment);
            memories.add(new MemoryPlacement(segment, anchor(rectangle.x(), size), anchor(rectangle.y(), size)));
        }
        return new BlueprintLayout(packing.width(), packing.height(), List.of(main), memories);
    }

    /** Exhaustively chooses a deterministic minimum-area first-fit packing for the current single shard. */
    private static Packing compactPacking(int processorSize, List<PhysicalMemoryLayout.Segment> segments) {
        int minimumWidth = processorSize;
        int maximumWidth = processorSize;
        for (PhysicalMemoryLayout.Segment segment : segments) {
            minimumWidth = Math.max(minimumWidth, memorySize(segment));
            maximumWidth += memorySize(segment);
        }
        Packing best = null;
        for (int width = minimumWidth; width <= maximumWidth; width++) {
            Packing candidate = packAtWidth(processorSize, segments, width, maximumWidth);
            if (best == null || candidate.area() < best.area()
                || candidate.area() == best.area() && candidate.maximumDimension() < best.maximumDimension()
                || candidate.area() == best.area() && candidate.maximumDimension() == best.maximumDimension()
                    && candidate.height() < best.height()) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best, "compact blueprint packing");
    }

    private static Packing packAtWidth(int processorSize, List<PhysicalMemoryLayout.Segment> segments,
                                       int width, int maximumHeight) {
        boolean[][] occupied = new boolean[maximumHeight][width];
        Rectangle processor = new Rectangle(0, 0, processorSize);
        occupy(occupied, processor);
        List<Rectangle> memories = new ArrayList<>();
        int usedWidth = processorSize;
        int usedHeight = processorSize;
        for (PhysicalMemoryLayout.Segment segment : segments) {
            int size = memorySize(segment);
            Rectangle placed = firstFree(occupied, size);
            occupy(occupied, placed);
            memories.add(placed);
            usedWidth = Math.max(usedWidth, placed.x() + size);
            usedHeight = Math.max(usedHeight, placed.y() + size);
        }
        return new Packing(usedWidth, usedHeight, processor, List.copyOf(memories));
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
        throw new IllegalArgumentException("无法在蓝图候选宽度内放置 Runtime Memory");
    }

    private static void occupy(boolean[][] occupied, Rectangle rectangle) {
        for (int y = rectangle.y(); y < rectangle.y() + rectangle.size(); y++) {
            for (int x = rectangle.x(); x < rectangle.x() + rectangle.size(); x++) occupied[y][x] = true;
        }
    }

    private static int memorySize(PhysicalMemoryLayout.Segment segment) {
        return segment.kind() == RuntimePreferences.MemoryKind.CELL ? 1 : 2;
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

    private record Packing(int width, int height, Rectangle processor, List<Rectangle> memories) {
        private int area() { return Math.multiplyExact(width, height); }
        private int maximumDimension() { return Math.max(width, height); }
    }
}
