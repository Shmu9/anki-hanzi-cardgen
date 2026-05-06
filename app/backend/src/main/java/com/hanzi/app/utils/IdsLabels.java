package com.hanzi.app.utils;

import java.util.Map;

public final class IdsLabels {
    static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("⿰", "left-right"),
            Map.entry("⿱", "above-below"),
            Map.entry("⿲", "left-middle-right"),
            Map.entry("⿳", "top-middle-bottom"),
            Map.entry("⿴", "surround"),
            Map.entry("⿵", "surround from above"),
            Map.entry("⿶", "surround from below"),
            Map.entry("⿷", "surround from left"),
            Map.entry("⿸", "surround upper-left"),
            Map.entry("⿹", "surround upper-right"),
            Map.entry("⿺", "surround lower-left"),
            Map.entry("⿻", "overlay"));

    private IdsLabels() {}

    public static boolean isIdsExpression(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return LABELS.containsKey(value.substring(0, value.offsetByCodePoints(0, 1)));
    }

    public static String labelFor(String value) {
        return value == null ? null : LABELS.get(value);
    }
}
