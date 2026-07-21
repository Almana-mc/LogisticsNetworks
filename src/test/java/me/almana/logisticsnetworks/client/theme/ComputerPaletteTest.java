package me.almana.logisticsnetworks.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputerPaletteTest {

    @Test
    void retainsClassicTerminalPalette() {
        assertEquals(0xFF80F2A3, ComputerPalette.CLASSIC.accent());
        assertEquals(0xFFD8F7DD, ComputerPalette.CLASSIC.text());
        assertEquals(0xFF72A7FF, ComputerPalette.CLASSIC.highlightBorder());
    }
}
