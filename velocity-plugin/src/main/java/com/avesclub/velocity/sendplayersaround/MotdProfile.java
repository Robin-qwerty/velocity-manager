package com.avesclub.velocity.sendplayersaround;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One fully-resolved MOTD profile (the {@code default:} profile, or one {@code domains:} entry
 * after inheritance has been applied). See {@link #fromYamlMap(Map, MotdProfile)} for how a
 * domain profile inherits from its fallback section-by-section and field-by-field.
 */
final class MotdProfile {

    private final FaviconSettings favicon;
    private final VersionNameSettings versionName;
    private final VersionProtocolSettings versionProtocol;
    private final PlayerCountSettings playerCount;
    private final HoverSettings hover;
    private final DescriptionSettings description;

    MotdProfile(
            FaviconSettings favicon,
            VersionNameSettings versionName,
            VersionProtocolSettings versionProtocol,
            PlayerCountSettings playerCount,
            HoverSettings hover,
            DescriptionSettings description
    ) {
        this.favicon = favicon;
        this.versionName = versionName;
        this.versionProtocol = versionProtocol;
        this.playerCount = playerCount;
        this.hover = hover;
        this.description = description;
    }

    FaviconSettings favicon() {
        return favicon;
    }

    VersionNameSettings versionName() {
        return versionName;
    }

    VersionProtocolSettings versionProtocol() {
        return versionProtocol;
    }

    PlayerCountSettings playerCount() {
        return playerCount;
    }

    HoverSettings hover() {
        return hover;
    }

    DescriptionSettings description() {
        return description;
    }

    /**
     * Parses one profile map. Any section missing from {@code raw} (or, within a present section,
     * any field missing from it) falls back to the matching value on {@code fallback} rather than a
     * hardcoded default — this is what lets a {@code domains:} entry only override what it needs to
     * while inheriting the rest from the user's own customized {@code default:} profile.
     */
    static MotdProfile fromYamlMap(Map<String, Object> raw, MotdProfile fallback) {
        Map<String, Object> m = raw == null ? Map.of() : raw;
        Map<String, Object> versionMap = asMap(m.get("version"));
        return new MotdProfile(
                FaviconSettings.fromYamlMap(m.get("favicon"), fallback.favicon()),
                VersionNameSettings.fromYamlMap(versionMap.get("name"), fallback.versionName()),
                VersionProtocolSettings.fromYamlMap(versionMap.get("protocol"), fallback.versionProtocol()),
                PlayerCountSettings.fromYamlMap(m.get("player-count"), fallback.playerCount()),
                HoverSettings.fromYamlMap(m.get("hover"), fallback.hover()),
                DescriptionSettings.fromYamlMap(m.get("description"), fallback.description())
        );
    }

    /**
     * Minimal hardcoded safety net used only when {@code motd.yml} itself cannot be read at all.
     */
    static MotdProfile builtinDefault() {
        return new MotdProfile(
                new FaviconSettings(false, FaviconSettings.Mode.SINGLE, List.of()),
                new VersionNameSettings(false, ""),
                new VersionProtocolSettings(false, -1),
                new PlayerCountSettings(false, OverrideValue.disabled(), OverrideValue.disabled()),
                new HoverSettings(false, false, List.of()),
                new DescriptionSettings(true, List.of("&aA Minecraft Server"))
        );
    }

    // ---- shared field helpers -------------------------------------------------------------

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

    private static boolean boolField(Map<String, Object> m, String key, boolean fallback) {
        if (!m.containsKey(key)) {
            return fallback;
        }
        Object v = m.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return fallback;
        }
        return Boolean.parseBoolean(v.toString().trim());
    }

    private static int intField(Map<String, Object> m, String key, int fallback) {
        if (!m.containsKey(key)) {
            return fallback;
        }
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String stringField(Map<String, Object> m, String key, String fallback) {
        if (!m.containsKey(key)) {
            return fallback;
        }
        Object v = m.get(key);
        return v == null ? fallback : v.toString();
    }

    private static List<String> stringListField(Map<String, Object> m, String key, List<String> fallback) {
        if (!m.containsKey(key)) {
            return fallback;
        }
        Object v = m.get(key);
        if (!(v instanceof List<?> list)) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return List.copyOf(out);
    }

    // ---- nested section types ---------------------------------------------------------------

    static final class FaviconSettings {
        enum Mode {
            SINGLE, RANDOM
        }

        private final boolean enabled;
        private final Mode mode;
        private final List<String> files;

        FaviconSettings(boolean enabled, Mode mode, List<String> files) {
            this.enabled = enabled;
            this.mode = mode;
            this.files = List.copyOf(files);
        }

        boolean enabled() {
            return enabled;
        }

        Mode mode() {
            return mode;
        }

        List<String> files() {
            return files;
        }

        static FaviconSettings fromYamlMap(Object raw, FaviconSettings fallback) {
            if (!(raw instanceof Map<?, ?> mm)) {
                return fallback;
            }
            Map<String, Object> m = asMap(mm);
            boolean enabled = boolField(m, "enabled", fallback.enabled());
            Mode mode = fallback.mode();
            if (m.containsKey("mode") && m.get("mode") != null) {
                try {
                    mode = Mode.valueOf(m.get("mode").toString().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    mode = fallback.mode();
                }
            }
            List<String> files = stringListField(m, "files", fallback.files());
            return new FaviconSettings(enabled, mode, files);
        }
    }

    static final class VersionNameSettings {
        private final boolean enabled;
        private final String text;

        VersionNameSettings(boolean enabled, String text) {
            this.enabled = enabled;
            this.text = text == null ? "" : text;
        }

        boolean enabled() {
            return enabled;
        }

        String text() {
            return text;
        }

        static VersionNameSettings fromYamlMap(Object raw, VersionNameSettings fallback) {
            if (!(raw instanceof Map<?, ?> mm)) {
                return fallback;
            }
            Map<String, Object> m = asMap(mm);
            return new VersionNameSettings(
                    boolField(m, "enabled", fallback.enabled()),
                    stringField(m, "text", fallback.text())
            );
        }
    }

    static final class VersionProtocolSettings {
        private final boolean enabled;
        private final int value;

        VersionProtocolSettings(boolean enabled, int value) {
            this.enabled = enabled;
            this.value = value;
        }

        boolean enabled() {
            return enabled;
        }

        int value() {
            return value;
        }

        static VersionProtocolSettings fromYamlMap(Object raw, VersionProtocolSettings fallback) {
            if (!(raw instanceof Map<?, ?> mm)) {
                return fallback;
            }
            Map<String, Object> m = asMap(mm);
            return new VersionProtocolSettings(
                    boolField(m, "enabled", fallback.enabled()),
                    intField(m, "value", fallback.value())
            );
        }
    }

    static final class OverrideValue {
        private final boolean enabled;
        private final int value;

        OverrideValue(boolean enabled, int value) {
            this.enabled = enabled;
            this.value = value;
        }

        static OverrideValue disabled() {
            return new OverrideValue(false, 0);
        }

        boolean enabled() {
            return enabled;
        }

        int value() {
            return value;
        }

        static OverrideValue fromYamlMap(Object raw, OverrideValue fallback) {
            if (!(raw instanceof Map<?, ?> mm)) {
                return fallback;
            }
            Map<String, Object> m = asMap(mm);
            return new OverrideValue(
                    boolField(m, "enabled", fallback.enabled()),
                    intField(m, "value", fallback.value())
            );
        }
    }

    static final class PlayerCountSettings {
        private final boolean hide;
        private final OverrideValue onlineOverride;
        private final OverrideValue maxOverride;

        PlayerCountSettings(boolean hide, OverrideValue onlineOverride, OverrideValue maxOverride) {
            this.hide = hide;
            this.onlineOverride = onlineOverride;
            this.maxOverride = maxOverride;
        }

        boolean hide() {
            return hide;
        }

        OverrideValue onlineOverride() {
            return onlineOverride;
        }

        OverrideValue maxOverride() {
            return maxOverride;
        }

        static PlayerCountSettings fromYamlMap(Object raw, PlayerCountSettings fallback) {
            if (!(raw instanceof Map<?, ?> mm)) {
                return fallback;
            }
            Map<String, Object> m = asMap(mm);
            boolean hide = boolField(m, "hide", fallback.hide());
            OverrideValue online = OverrideValue.fromYamlMap(m.get("online-override"), fallback.onlineOverride());
            OverrideValue max = OverrideValue.fromYamlMap(m.get("max-override"), fallback.maxOverride());
            return new PlayerCountSettings(hide, online, max);
        }
    }

    static final class HoverSettings {
        private final boolean enabled;
        private final boolean vanilla;
        private final List<String> lines;

        HoverSettings(boolean enabled, boolean vanilla, List<String> lines) {
            this.enabled = enabled;
            this.vanilla = vanilla;
            this.lines = List.copyOf(lines);
        }

        boolean enabled() {
            return enabled;
        }

        boolean vanilla() {
            return vanilla;
        }

        List<String> lines() {
            return lines;
        }

        static HoverSettings fromYamlMap(Object raw, HoverSettings fallback) {
            if (!(raw instanceof Map<?, ?> mm)) {
                return fallback;
            }
            Map<String, Object> m = asMap(mm);
            return new HoverSettings(
                    boolField(m, "enabled", fallback.enabled()),
                    boolField(m, "vanilla", fallback.vanilla()),
                    stringListField(m, "lines", fallback.lines())
            );
        }
    }

    static final class DescriptionSettings {
        private final boolean enabled;
        private final List<String> lines;

        DescriptionSettings(boolean enabled, List<String> lines) {
            this.enabled = enabled;
            this.lines = List.copyOf(lines);
        }

        boolean enabled() {
            return enabled;
        }

        List<String> lines() {
            return lines;
        }

        static DescriptionSettings fromYamlMap(Object raw, DescriptionSettings fallback) {
            if (!(raw instanceof Map<?, ?> mm)) {
                return fallback;
            }
            Map<String, Object> m = asMap(mm);
            return new DescriptionSettings(
                    boolField(m, "enabled", fallback.enabled()),
                    stringListField(m, "lines", fallback.lines())
            );
        }
    }
}
