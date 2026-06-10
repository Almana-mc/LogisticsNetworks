package me.almana.logisticsnetworks.client.theme;

import me.almana.logisticsnetworks.data.ChannelType;

public final class ChannelTint {

    public static int hue(ChannelType type) {
        return switch (type) {
            case ITEM -> 0xFFF5A623;
            case FLUID -> 0xFF35C7F0;
            case ENERGY -> 0xFFF24D4D;
            case CHEMICAL -> 0xFF4FD16B;
            case SOURCE -> 0xFFB45CFF;
        };
    }

    public static int digit(ChannelType type, Theme theme) {
        return shade(hue(type), theme);
    }

    public static int tabBg(ChannelType type, Theme theme) {
        float t = isLight(theme.surface2()) ? 0.16f : 0.20f;
        return blend(theme.surface2(), hue(type), t);
    }

    public static int border(ChannelType type, Theme theme) {
        return blend(theme.border(), digit(type, theme), 0.65f);
    }

    public static int selectedBg(ChannelType type, Theme theme) {
        return shade(hue(type), theme);
    }

    public static int selectedFg(ChannelType type, Theme theme) {
        return contrastText(selectedBg(type, theme));
    }

    static int shade(int color, Theme theme) {
        return isLight(theme.surface2()) ? blend(color, 0xFF000000, 0.28f) : color;
    }

    static boolean isLight(int argb) {
        return luminance(argb) > 0.5f;
    }

    static int contrastText(int argb) {
        return luminance(argb) > 0.5f ? 0xFF111111 : 0xFFFFFFFF;
    }

    static int blend(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * t);
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static float luminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
    }

    private ChannelTint() {
    }
}
