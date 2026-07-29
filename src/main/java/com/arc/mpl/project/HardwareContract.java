package com.arc.mpl.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Parsed external hardware declarations. Game aliases remain deployment-time facts. */
public record HardwareContract(
    List<LinkDeclaration> links,
    Map<String, String> messages,
    Map<String, Resource> resources
) {
    public HardwareContract {
        links = List.copyOf(links);
        messages = Map.copyOf(messages);
        resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        if (links.stream().map(LinkDeclaration::gameAlias).distinct().count() != links.size()) {
            throw new IllegalArgumentException("物理硬件链接 alias 重复");
        }
        for (Map.Entry<String, Resource> entry : resources.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().mplName())) {
                throw new IllegalArgumentException("硬件逻辑资源 key 与名称不一致：" + entry.getKey());
            }
            if (!links.containsAll(entry.getValue().physicalLinks())) {
                throw new IllegalArgumentException("硬件逻辑资源引用了契约外链接：" + entry.getKey());
            }
        }
    }

    public HardwareContract(List<LinkDeclaration> links, Map<String, String> messages) {
        this(links, messages, directResources(links));
    }

    public Optional<Resource> resource(String name) {
        return Optional.ofNullable(resources.get(name));
    }

    public Set<String> names() {
        return resources.keySet();
    }

    public record LinkDeclaration(String mplName, String mplType, String gameAlias, int width, int height) {
        public LinkDeclaration {
            Objects.requireNonNull(mplName, "mplName");
            Objects.requireNonNull(mplType, "mplType");
            Objects.requireNonNull(gameAlias, "gameAlias");
            if (width < 0 || height < 0 || (width == 0) != (height == 0)) {
                throw new IllegalArgumentException("硬件链接宽高必须同时为正数或同时未知：" + mplName);
            }
            if (!"Display".equals(mplType) && width != 0) {
                throw new IllegalArgumentException("只有 Display 链接可声明宽高：" + mplName);
            }
        }

        public LinkDeclaration(String mplName, String mplType, String gameAlias) {
            this(mplName, mplType, gameAlias, 0, 0);
        }
    }

    public record Resource(String mplName, String mplType, List<LinkDeclaration> physicalLinks,
                           Optional<DisplayLayout> display) {
        public Resource {
            Objects.requireNonNull(mplName, "mplName");
            Objects.requireNonNull(mplType, "mplType");
            physicalLinks = List.copyOf(physicalLinks);
            display = Objects.requireNonNull(display, "display");
            if (physicalLinks.isEmpty()) throw new IllegalArgumentException("硬件逻辑资源必须引用至少一个物理链接：" + mplName);
            if (display.isPresent() && !"Display".equals(mplType)) {
                throw new IllegalArgumentException("非 Display 资源不能拥有显示布局：" + mplName);
            }
        }

        public boolean directlyLinked() {
            return physicalLinks.size() == 1 && physicalLinks.get(0).mplName().equals(mplName);
        }
    }

    public record DisplayLayout(int width, int height, List<DisplayTile> tiles) {
        public DisplayLayout {
            if (width < 1 || height < 1) throw new IllegalArgumentException("Display 逻辑尺寸必须为正数");
            tiles = List.copyOf(tiles);
            if (tiles.isEmpty()) throw new IllegalArgumentException("Display 布局至少需要一个物理屏幕");
            for (DisplayTile tile : tiles) {
                if ((long) tile.x() + tile.width() > width || (long) tile.y() + tile.height() > height) {
                    throw new IllegalArgumentException("Display 成员越出逻辑画布：" + tile.mplName());
                }
            }
        }
    }

    public record DisplayTile(String mplName, String gameAlias, int x, int y, int width, int height) {
        public DisplayTile {
            Objects.requireNonNull(mplName, "mplName");
            Objects.requireNonNull(gameAlias, "gameAlias");
            if (x < 0 || y < 0 || width < 1 || height < 1) {
                throw new IllegalArgumentException("Display 成员坐标和尺寸无效：" + mplName);
            }
        }
    }

    private static Map<String, Resource> directResources(List<LinkDeclaration> links) {
        Map<String, Resource> result = new LinkedHashMap<>();
        for (LinkDeclaration link : links) {
            Optional<DisplayLayout> display = Optional.empty();
            if ("Display".equals(link.mplType()) && link.width() > 0) {
                display = Optional.of(new DisplayLayout(link.width(), link.height(),
                    List.of(new DisplayTile(link.mplName(), link.gameAlias(), 0, 0, link.width(), link.height()))));
            }
            if (result.put(link.mplName(), new Resource(link.mplName(), link.mplType(), List.of(link), display)) != null) {
                throw new IllegalArgumentException("重复的硬件逻辑资源：" + link.mplName());
            }
        }
        return result;
    }
}
