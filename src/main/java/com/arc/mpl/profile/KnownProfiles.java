package com.arc.mpl.profile;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Built-in profiles loaded from audited, versioned JSON resources. */
public final class KnownProfiles {
    private static final Map<String, TargetProfile> PROFILES = loadBuiltins();

    private KnownProfiles() {
    }

    public static Optional<TargetProfile> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(PROFILES.get(id.toLowerCase(Locale.ROOT)));
    }

    private static Map<String, TargetProfile> loadBuiltins() {
        Map<String, TargetProfile> profiles = new LinkedHashMap<>();
        loadBuiltin(profiles, "v146");
        loadBuiltin(profiles, "v159.7");
        return Map.copyOf(profiles);
    }

    private static void loadBuiltin(Map<String, TargetProfile> profiles, String id) {
        String resource = "/com/arc/mpl/profile/" + id + ".json";
        InputStream source = KnownProfiles.class.getResourceAsStream(resource);
        TargetProfile profile = TargetProfileLoader.load(source);
        if (!profile.id().equalsIgnoreCase(id)) {
            throw new IllegalStateException("目标配置资源 ID 不匹配：" + resource);
        }
        if (profiles.put(id.toLowerCase(Locale.ROOT), profile) != null) {
            throw new IllegalStateException("重复的内置 target profile：" + id);
        }
    }
}
