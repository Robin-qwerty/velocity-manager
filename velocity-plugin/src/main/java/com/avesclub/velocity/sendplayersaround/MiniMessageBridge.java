package com.avesclub.velocity.sendplayersaround;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets legacy {@code &} color/format codes (including {@code &#RRGGBB} hex) and native
 * MiniMessage tags (gradients, {@code <rainbow>}, ...) coexist in the same MOTD string: legacy
 * codes are converted to MiniMessage tags first, then the whole string is parsed as MiniMessage.
 * Tags that are already MiniMessage syntax never match the legacy patterns below, so they pass
 * through untouched.
 */
final class MiniMessageBridge {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"), Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private MiniMessageBridge() {
    }

    static String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String withHex = HEX_PATTERN.matcher(input).replaceAll(m -> "<#" + m.group(1) + ">");

        Matcher matcher = CODE_PATTERN.matcher(withHex);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String tag = TAGS.get(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(out, Matcher.quoteReplacement(tag == null ? "" : tag));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static Component render(String input) {
        return MiniMessage.miniMessage().deserialize(legacyToMiniMessage(input));
    }
}
