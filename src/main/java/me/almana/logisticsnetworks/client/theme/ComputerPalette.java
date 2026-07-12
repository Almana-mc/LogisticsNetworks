package me.almana.logisticsnetworks.client.theme;

import me.almana.logisticsnetworks.ClientConfig;

public record ComputerPalette(
        int frame,
        int frameEdge,
        int frameInner,
        int screen,
        int panel,
        int panelAlt,
        int panelHeader,
        int border,
        int borderBright,
        int row,
        int rowHover,
        int rowSelected,
        int text,
        int textSecondary,
        int textMuted,
        int accent,
        int accentDark,
        int warning,
        int scanline,
        int badgeBg,
        int badgeText,
        int graph,
        int graphGrid,
        int highlightBg,
        int highlightHover,
        int highlightBorder,
        int lampOff,
        int lampOffGlow,
        int lampOn,
        int lampOnGlow,
        int lampBase,
        int starEmpty) {

    public static final ComputerPalette CLASSIC = new ComputerPalette(
            0xE0181E1A, 0xFF73806F, 0xC0080E0B, 0xD00C130F,
            0xF0101713, 0xFF152019, 0xFF1C2B22,
            0xFF4D6654, 0xFF96D9A9,
            0xFF14201A, 0xFF192920, 0xFF23382B,
            0xFFD8F7DD, 0xFF88B693, 0xFF587263,
            0xFF80F2A3, 0xFF315D3B, 0xFFE4CA7D, 0x1200FF88,
            0xFF17221A, 0xFFA4FDBB,
            0xFF6EE896, 0xFF213529,
            0xFF1B2640, 0xFF25355B, 0xFF72A7FF,
            0xFF5F7568, 0xFF37443D, 0xFF72A7FF, 0xFFBED6FF, 0xFF8FA39C,
            0xFF4A5A4E);

    private static ComputerPalette derived;
    private static Theme derivedFrom;

    public static ComputerPalette active() {
        if (ClientConfig.computerClassicTheme) {
            return CLASSIC;
        }
        Theme theme = ThemeState.active();
        if (derived == null || derivedFrom != theme) {
            derived = fromTheme(theme);
            derivedFrom = theme;
        }
        return derived;
    }

    public static ComputerPalette fromTheme(Theme theme) {
        return new ComputerPalette(
                withAlpha(theme.surface(), 0xE0),
                theme.borderStrong(),
                withAlpha(theme.bg(), 0xC0),
                withAlpha(theme.bg(), 0xD0),
                withAlpha(theme.surface(), 0xF0),
                theme.surface2(),
                theme.surfaceSunken(),
                theme.border(),
                theme.accent(),
                theme.surfaceSunken(),
                theme.surface2(),
                theme.accentSoft(),
                theme.text(),
                theme.textMuted(),
                theme.textSubtle(),
                theme.accent(),
                theme.accentSoft(),
                theme.warn(),
                withAlpha(theme.accent(), 0x12),
                theme.surfaceSunken(),
                theme.accent(),
                theme.accent(),
                theme.border(),
                theme.infoSoft(),
                ThemePaint.brighten(theme.infoSoft(), 0x10),
                theme.info(),
                theme.textSubtle(),
                theme.surfaceSunken(),
                theme.info(),
                ThemePaint.brighten(theme.info(), 0x40),
                theme.textMuted(),
                theme.textSubtle());
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
