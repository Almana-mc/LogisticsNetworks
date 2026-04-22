package me.almana.logisticsnetworks.client.theme;

public record Theme(
        String id,
        String label,
        int bg,
        int surface,
        int surface2,
        int surfaceSunken,
        int border,
        int borderStrong,
        int text,
        int textMuted,
        int textSubtle,
        int accent,
        int accentSoft,
        int danger,
        int dangerSoft,
        int warn,
        int warnSoft,
        int info,
        int infoSoft,
        int slotBg,
        int slotBorder,
        int tabActiveBg,
        int tabActiveFg,
        int shadow,
        boolean sharpCorners,
        boolean hardShadow,
        boolean glow
) {

    public int variantFg(Variant variant) {
        return switch (variant) {
            case ACCENT -> accent;
            case WARN -> warn;
            case DANGER -> danger;
            case INFO -> info;
            case NEUTRAL -> text;
        };
    }

    public int variantBg(Variant variant) {
        return switch (variant) {
            case ACCENT -> accentSoft;
            case WARN -> warnSoft;
            case DANGER -> dangerSoft;
            case INFO -> infoSoft;
            case NEUTRAL -> surfaceSunken;
        };
    }

    public enum Variant {
        ACCENT, WARN, DANGER, INFO, NEUTRAL
    }
}
