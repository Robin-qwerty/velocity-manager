package com.avesclub.velocity.sendplayersaround;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code motd.yml} for the Velocity plugin, following the same manual-parsing pattern as
 * {@link PluginConfig}. Domain profiles are fully resolved (inheritance already applied) at load
 * time, so {@link #resolve(String)} is a plain map lookup with no per-ping fallback chasing.
 */
final class MotdConfig {

    private final boolean enabled;
    private final MotdProfile defaultProfile;
    private final Map<String, MotdProfile> domainProfiles;

    MotdConfig(boolean enabled, MotdProfile defaultProfile, Map<String, MotdProfile> domainProfiles) {
        this.enabled = enabled;
        this.defaultProfile = defaultProfile == null ? MotdProfile.builtinDefault() : defaultProfile;
        this.domainProfiles = domainProfiles == null ? Map.of() : Map.copyOf(domainProfiles);
    }

    boolean enabled() {
        return enabled;
    }

    MotdProfile defaultProfile() {
        return defaultProfile;
    }

    Map<String, MotdProfile> domainProfiles() {
        return domainProfiles;
    }

    MotdProfile resolve(String host) {
        MotdProfile profile = domainProfiles.get(host);
        return profile == null ? defaultProfile : profile;
    }

    static void ensureDefaultFile(Path dataDirectory, Class<?> resourceAnchor) throws IOException {
        if (Files.notExists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        Path motdPath = dataDirectory.resolve("motd.yml");
        if (Files.exists(motdPath)) {
            return;
        }
        try (InputStream in = resourceAnchor.getResourceAsStream("/motd.yml")) {
            if (in == null) {
                throw new IOException("Missing bundled /motd.yml");
            }
            Files.copy(in, motdPath);
        }
    }

    static MotdConfig load(Path dataDirectory) throws IOException {
        Path motdPath = dataDirectory.resolve("motd.yml");
        if (!Files.exists(motdPath)) {
            return defaults();
        }
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(Files.newBufferedReader(motdPath));
        if (root == null) {
            return defaults();
        }
        Object enabledObj = root.get("enabled");
        boolean enabled = enabledObj instanceof Boolean bool
                ? bool
                : enabledObj != null && Boolean.parseBoolean(enabledObj.toString().trim());

        MotdProfile defaultProfile = MotdProfile.fromYamlMap(asMap(root.get("default")), MotdProfile.builtinDefault());

        Map<String, MotdProfile> domains = new LinkedHashMap<>();
        Object rawDomains = root.get("domains");
        if (rawDomains instanceof Map<?, ?> domainsMap) {
            for (Map.Entry<?, ?> e : domainsMap.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String host = HostUtil.stripHostPort(e.getKey().toString().trim().toLowerCase(Locale.ROOT));
                if (host.isEmpty()) {
                    continue;
                }
                domains.put(host, MotdProfile.fromYamlMap(asMap(e.getValue()), defaultProfile));
            }
        }

        return new MotdConfig(enabled, defaultProfile, domains);
    }

    static MotdConfig defaults() {
        return new MotdConfig(false, MotdProfile.builtinDefault(), Map.of());
    }

    private static Map<String, Object> asMap(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) {
                out.put(e.getKey().toString(), e.getValue());
            }
        }
        return out;
    }
}
