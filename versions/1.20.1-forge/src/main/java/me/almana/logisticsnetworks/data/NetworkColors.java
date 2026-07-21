package me.almana.logisticsnetworks.data;

import java.util.Random;

public final class NetworkColors {

    public static final int DEFAULT = 0xFFFFFF;

    private static final Random RANDOM = new Random();

    private NetworkColors() {
    }

    public static int mask(int rgb) {
        return rgb & 0xFFFFFF;
    }

    public static int randomColor() {
        return hsvToRgb(RANDOM.nextFloat(), 0.65f, 0.95f);
    }

    public static int hsvToRgb(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        s = clamp01(s);
        v = clamp01(v);
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1f - s);
        float q = v * (1f - f * s);
        float t = v * (1f - (1f - f) * s);
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (to255(r) << 16) | (to255(g) << 8) | to255(b);
    }

    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h = 0f;
        if (delta > 0f) {
            if (max == r) {
                h = ((g - b) / delta) % 6f;
            } else if (max == g) {
                h = (b - r) / delta + 2f;
            } else {
                h = (r - g) / delta + 4f;
            }
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        float s = max == 0f ? 0f : delta / max;
        return new float[] { h, s, max };
    }

    public static String toHex(int rgb) {
        return String.format("%06X", mask(rgb));
    }

    public static int parseHex(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String clean = value.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        if (clean.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(clean, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int to255(float channel) {
        return Math.max(0, Math.min(255, Math.round(channel * 255f)));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
