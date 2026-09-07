package com.avesclub.velocity.sendplayersaround;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Implements the {@code <center>} placeholder: a line starting with the literal token is padded
 * with spaces so it appears roughly centered in the client's server-list line. This is a pixel
 * width *approximation* of the vanilla non-unicode Minecraft font (not pixel-perfect, ignores
 * resource-pack fonts and bold-glyph widening) using a small static lookup table, not a font
 * metrics library.
 */
final class MotdCenterUtil {

    private static final String TOKEN = "<center>";
    private static final int TARGET_WIDTH_PX = 230;
    private static final int DEFAULT_WIDTH_PX = 6;
    private static final int SPACE_WIDTH_PX = 4;

    // Strips legacy codes, hex codes, and MiniMessage-style tags so only visible characters are
    // measured. The padding is still prepended to the original (tagged) string, not this one.
    private static final Pattern STRIP_FOR_MEASURE = Pattern.compile("(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]|<[^<>]*>");

    private static final Map<Character, Integer> WIDTHS = buildWidthTable();

    private MotdCenterUtil() {
    }

    static String applyCenterIfPresent(String line) {
        if (line == null || !line.startsWith(TOKEN)) {
            return line;
        }
        String remainder = line.substring(TOKEN.length());
        String plain = STRIP_FOR_MEASURE.matcher(remainder).replaceAll("");
        int textWidth = measureWidth(plain);
        int diff = TARGET_WIDTH_PX - textWidth;
        if (diff <= 0) {
            return remainder;
        }
        int spaces = (diff / 2) / SPACE_WIDTH_PX;
        return " ".repeat(Math.max(0, spaces)) + remainder;
    }

    private static int measureWidth(String plainText) {
        int total = 0;
        for (int i = 0; i < plainText.length(); i++) {
            char c = plainText.charAt(i);
            total += (c == ' ') ? SPACE_WIDTH_PX : WIDTHS.getOrDefault(c, DEFAULT_WIDTH_PX);
        }
        return total;
    }

    private static Map<Character, Integer> buildWidthTable() {
        Map<Character, Integer> m = new HashMap<>();
        for (char c : "il.,':!|".toCharArray()) {
            m.put(c, 3);
        }
        for (char c : "abcdeghknopqsuvxyz".toCharArray()) {
            m.put(c, 5);
        }
        for (char c : "fjrt".toCharArray()) {
            m.put(c, 4);
        }
        m.put('m', 7);
        m.put('w', 7);
        for (char c : "ABCDEFGHKNOPQRSUVXYZ".toCharArray()) {
            m.put(c, 6);
        }
        m.put('I', 3);
        m.put('J', 5);
        m.put('L', 5);
        m.put('M', 7);
        m.put('T', 5);
        m.put('W', 7);
        for (char c : "0123456789".toCharArray()) {
            m.put(c, 5);
        }
        return m;
    }
}
