package me.almana.logisticsnetworks.client.screen;

import java.util.Map;

final class FilterScreenText {
    private static final Map<String, String> PATH_ABBREV = Map.of(
            "enchantments", "ench",
            "stored_enchantments", "stored",
            "potion_contents", "potion",
            "custom_data", "data",
            "attribute_modifiers", "attr"
    );

    private FilterScreenText() {
    }

    static boolean isBooleanValue(String displayVal) {
        return "true".equals(displayVal) || "false".equals(displayVal);
    }

    static String formatNbtPath(String path) {
        String[] segments = path.split("\\.");
        String last = null;
        String parent = null;
        for (String segment : segments) {
            if (segment.equals("levels")) {
                continue;
            }
            String clean = stripNamespace(segment);
            parent = last;
            last = clean;
        }
        if (parent != null && last != null) {
            String abbr = PATH_ABBREV.getOrDefault(parent, parent);
            return abbr + " > " + last;
        }
        return last != null ? last : path;
    }

    static String formatNbtValue(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return stripNamespace(raw.substring(1, raw.length() - 1));
        }
        if (raw.endsWith("b") || raw.endsWith("s") || raw.endsWith("L")
                || raw.endsWith("f") || raw.endsWith("d")) {
            String num = raw.substring(0, raw.length() - 1);
            if (raw.endsWith("b")) {
                if ("0".equals(num)) {
                    return "false";
                }
                if ("1".equals(num)) {
                    return "true";
                }
            }
            return num;
        }
        return raw;
    }

    static String commonPrefix(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return a.substring(0, i);
    }

    static String abbreviateNbtPath(String path) {
        StringBuilder result = new StringBuilder();
        String[] segments = path.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                result.append(".");
            }
            String segment = segments[i];
            int colon = segment.indexOf(':');
            if (colon > 4) {
                result.append(segment, 0, 4).append(segment.substring(colon));
            } else {
                result.append(segment);
            }
        }
        return result.toString();
    }

    private static String stripNamespace(String value) {
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1) : value;
    }
}
