package me.almana.logisticsnetworks.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkColorsTest {

    @Test
    void convertsPrimaryHues() {
        assertEquals(0xFF0000, NetworkColors.hsvToRgb(0f, 1f, 1f));
        assertEquals(0x00FF00, NetworkColors.hsvToRgb(1f / 3f, 1f, 1f));
        assertEquals(0x0000FF, NetworkColors.hsvToRgb(2f / 3f, 1f, 1f));
    }

    @Test
    void parsesAndMasksHexColors() {
        assertEquals(0x12ABEF, NetworkColors.parseHex("#12abef", 0));
        assertEquals(0xABCDEF, NetworkColors.mask(0xFFABCDEF));
        assertEquals(0x112233, NetworkColors.parseHex("invalid", 0x112233));
    }

    @Test
    void roundTripsRgbAndHsv() {
        int color = 0x6D3AC7;
        float[] hsv = NetworkColors.rgbToHsv(color);
        assertEquals(color, NetworkColors.hsvToRgb(hsv[0], hsv[1], hsv[2]));
        assertArrayEquals(hsv, NetworkColors.rgbToHsv(NetworkColors.hsvToRgb(hsv[0], hsv[1], hsv[2])), 0.01f);
    }
}
