package com.arc.mpl.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Named non-memory hardware requirements declared by a package {@code .mplh}. */
public record PackageHardwareInterface(Map<String, Requirement> requirements) {
    public PackageHardwareInterface {
        requirements = Collections.unmodifiableMap(new LinkedHashMap<>(
            Objects.requireNonNull(requirements, "requirements")));
    }

    public static PackageHardwareInterface empty() {
        return new PackageHardwareInterface(Map.of());
    }

    public record Requirement(String name, String type, Access access, int minimumWidth, int minimumHeight, int count) {
        public Requirement {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("包硬件需求名称不能为空");
            if (type == null || type.isBlank()) throw new IllegalArgumentException("包硬件需求类型不能为空：" + name);
            Objects.requireNonNull(access, "access");
            if (minimumWidth < 0 || minimumHeight < 0) throw new IllegalArgumentException("Display 最小尺寸不得为负数：" + name);
            if (count < 1) throw new IllegalArgumentException("硬件需求 count 必须大于 0：" + name);
            if (!"Display".equals(type) && (minimumWidth != 0 || minimumHeight != 0)) {
                throw new IllegalArgumentException("只有 Display 可声明 minWidth/minHeight：" + name);
            }
            if (count != 1) {
                throw new IllegalArgumentException("第一版 require 仅支持 count: 1：" + name);
            }
        }
    }

    public enum Access {
        READ,
        WRITE,
        READ_WRITE
    }
}
