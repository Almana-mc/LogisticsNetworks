package me.almana.logisticsnetworks.client.theme;

import java.util.List;

public final class Themes {

    public static final Theme LIGHT = new Theme(
            "light", "Light",
            0xFFF4F3EF, 0xFFFFFFFF, 0xFFFAFAF7, 0xFFEEEDE8,
            0xFFE3E1D9, 0xFFC9C6BB,
            0xFF1A1A1A, 0xFF6B6B66, 0xFFA3A09A,
            0xFF2F6F4F, 0xFFDCEAD9,
            0xFFA83232, 0xFFF5DCDC,
            0xFFB5721B, 0xFFF6E6CC,
            0xFF6B4BBD, 0xFFE6E0F5,
            0xFFF0EEE6, 0xFFD6D3C8,
            0xFF1A1A1A, 0xFFFFFFFF,
            0x20000000,
            false, false, false);

    public static final Theme DARK = new Theme(
            "dark", "Dark",
            0xFF111214, 0xFF1C1D20, 0xFF232428, 0xFF17181A,
            0xFF2A2C30, 0xFF3A3D42,
            0xFFECECEC, 0xFF8A8D93, 0xFF5A5D63,
            0xFF6EE7A8, 0x236EE7A8,
            0xFFFF7A7A, 0x23FF7A7A,
            0xFFF2B45E, 0x23F2B45E,
            0xFFB39BFF, 0x23B39BFF,
            0xFF151619, 0xFF2D2F34,
            0xFFECECEC, 0xFF111214,
            0xA0000000,
            false, false, false);

    public static final Theme REDSTONE = new Theme(
            "redstone", "Redstone",
            0xFF1A0F0F, 0xFF251513, 0xFF2E1A17, 0xFF1A0D0C,
            0xFF3A1F1B, 0xFF5A2A24,
            0xFFF5E8E0, 0xFFB89188, 0xFF7A5A52,
            0xFFFF5A3C, 0x28FF5A3C,
            0xFFFF3D3D, 0x2AFF3D3D,
            0xFFFFB347, 0x23FFB347,
            0xFFD4A3FF, 0x23D4A3FF,
            0xFF1A0D0C, 0xFF3A1F1B,
            0xFFFF5A3C, 0xFF1A0F0F,
            0xA0000000,
            false, false, true);

    public static final Theme NEBULA = new Theme(
            "nebula", "Nebula",
            0xFF0B0D1A, 0xFF141627, 0xFF1A1D33, 0xFF0F1120,
            0xFF252940, 0xFF343A5A,
            0xFFEEF1FF, 0xFF8A90B5, 0xFF585D7E,
            0xFF5EEAD4, 0x235EEAD4,
            0xFFF87171, 0x23F87171,
            0xFFFBBF24, 0x23FBBF24,
            0xFFA78BFA, 0x28A78BFA,
            0xFF0F1120, 0xFF252940,
            0xFF5EEAD4, 0xFF0B0D1A,
            0xB0000000,
            false, false, true);

    public static final Theme GLASS = new Theme(
            "glass", "Glass",
            0xFFB9CCE6, 0xE0FFFFFF, 0xC8FFFFFF, 0xA8FFFFFF,
            0xFFFFFFFF, 0xFFE8E6EF,
            0xFF1E1B33, 0xFF5A5570, 0xFF8A85A0,
            0xFF5B4AED, 0x235B4AED,
            0xFFDC2F5A, 0x23DC2F5A,
            0xFFD97706, 0x23D97706,
            0xFF0284C7, 0x230284C7,
            0xC0FFFFFF, 0xFFE0DEF0,
            0xFF1E1B33, 0xFFFFFFFF,
            0x30000000,
            false, false, false);

    public static final Theme TERMINAL = new Theme(
            "terminal", "Terminal",
            0xFF020605, 0xFF061210, 0xFF0A1A16, 0xFF030A08,
            0xFF0F3028, 0xFF1A5040,
            0xFF6AFC9A, 0xFF3AA06A, 0xFF1F6040,
            0xFF6AFC9A, 0x206AFC9A,
            0xFFFF5F5F, 0x2AFF5F5F,
            0xFFF5C542, 0x23F5C542,
            0xFF5FD1FF, 0x235FD1FF,
            0xFF030A08, 0xFF0F3028,
            0xFF6AFC9A, 0xFF020605,
            0x806AFC9A,
            false, false, true);

    public static final Theme PASTEL = new Theme(
            "pastel", "Pastel",
            0xFFFDF6F9, 0xFFFFFFFF, 0xFFFDF2F5, 0xFFF7E9ED,
            0xFFF0D9E0, 0xFFD9B3BF,
            0xFF3A2838, 0xFF8A6878, 0xFFBAA0AA,
            0xFFD96A8E, 0xFFFDE2EA,
            0xFFC94A5E, 0xFFFBDDE3,
            0xFFD98E3A, 0xFFFBE8D0,
            0xFF8A6AD9, 0xFFE8DFFB,
            0xFFFDF2F5, 0xFFF0D9E0,
            0xFFD96A8E, 0xFFFFFFFF,
            0x25000000,
            false, false, false);

    public static final Theme BRUTALIST = new Theme(
            "brutalist", "Brutalist",
            0xFFFEF7D7, 0xFFFFFFFF, 0xFFFEF7D7, 0xFFFAE89A,
            0xFF000000, 0xFF000000,
            0xFF000000, 0xFF2A2A2A, 0xFF6A6A6A,
            0xFF00AAFF, 0xFFB8E6FF,
            0xFFFF3D6E, 0xFFFFD4DE,
            0xFFFF8C00, 0xFFFFDFB8,
            0xFF8B5CF6, 0xFFD9C8FA,
            0xFFFFFFFF, 0xFF000000,
            0xFF000000, 0xFFFEF7D7,
            0xFF000000,
            true, true, false);

    public static final List<Theme> ALL = List.of(
            LIGHT, DARK, REDSTONE, NEBULA, GLASS, TERMINAL, PASTEL, BRUTALIST);

    public static Theme byId(String id) {
        for (Theme t : ALL) {
            if (t.id().equals(id)) return t;
        }
        return DARK;
    }

    private Themes() {}
}
