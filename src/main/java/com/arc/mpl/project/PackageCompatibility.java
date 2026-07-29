package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;

import java.util.ArrayList;
import java.util.List;

/** Validates package versions and target-profile requirements. */
final class PackageCompatibility {
    private PackageCompatibility() {
    }

    static void requireSemanticVersion(String value, String packageName) {
        if (!value.matches("0|[1-9][0-9]*(?:\\.(?:0|[1-9][0-9]*)){2}(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?")) {
            throw new IllegalArgumentException("包 " + packageName + " 的 version 不是 SemVer：" + value);
        }
    }

    static void validate(ProjectManifest manifest, TargetProfile profile) {
        manifest.requires().minimumMindustry().ifPresent(minimum -> {
            if (compareTarget(profile.id(), minimum) < 0) {
                throw new IllegalArgumentException("包 " + manifest.name() + " 要求 Mindustry >= " + minimum
                    + "，当前 target 为 " + profile.id());
            }
        });
        List<String> missing = manifest.requires().capabilities().stream()
            .filter(capability -> !profile.capabilities().contains(capability)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("包 " + manifest.name() + " 缺少 target 能力：" + missing);
        }
    }

    private static int compareTarget(String left, String right) {
        List<Integer> leftParts = targetParts(left);
        List<Integer> rightParts = targetParts(right);
        int size = Math.max(leftParts.size(), rightParts.size());
        for (int index = 0; index < size; index++) {
            int comparison = Integer.compare(index < leftParts.size() ? leftParts.get(index) : 0,
                index < rightParts.size() ? rightParts.get(index) : 0);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static List<Integer> targetParts(String value) {
        if (value == null || !value.matches("v[0-9]+(?:\\.[0-9]+)*")) {
            throw new IllegalArgumentException("Mindustry profile 版本格式无效：" + value);
        }
        List<Integer> result = new ArrayList<>();
        for (String part : value.substring(1).split("\\.")) result.add(Integer.parseInt(part));
        return result;
    }
}
