package com.arc.mpl.project;

import com.arc.mpl.profile.TargetProfile;

/** Target-profile checks shared by installation, lock verification, and import binding. */
final class PackageHardwareValidator {
    private PackageHardwareValidator() {
    }

    static void validate(PackageHardwareInterface hardwareInterface, TargetProfile profile, String packageName) {
        for (PackageHardwareInterface.Requirement requirement : hardwareInterface.requirements().values()) {
            if (profile.buildingType(requirement.type()).isEmpty()) {
                throw new IllegalArgumentException("包 " + packageName + " 的硬件类型不受 target " + profile.id()
                    + " 支持：" + requirement.type());
            }
            if (!supportsAccess(requirement, profile)) {
                throw new IllegalArgumentException("包 " + packageName + " 的硬件访问要求不受 target " + profile.id()
                    + " 支持：" + requirement.name() + "（" + requirement.access() + "）");
            }
        }
    }

    static boolean supportsAccess(PackageHardwareInterface.Requirement requirement, TargetProfile profile) {
        TargetProfile.BuildingType type = profile.buildingType(requirement.type()).orElse(null);
        if (type == null) return false;
        boolean readable = !type.propertyTypes().isEmpty();
        boolean writable = !type.actions().isEmpty()
            || "Display".equals(requirement.type()) || "Message".equals(requirement.type());
        return switch (requirement.access()) {
            case READ -> readable;
            case WRITE -> writable;
            case READ_WRITE -> readable && writable;
        };
    }
}
