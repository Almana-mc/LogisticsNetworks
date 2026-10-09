package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.network.SyncServerRackPayload.Side;
import net.minecraft.client.gui.Font;

final class RackChannelGrid {

    private static final int CELL_W = 34;
    private static final int CELL_H = 14;
    private static final int GAP = 3;
    static final int W = 3 * CELL_W + 2 * GAP + 8;
    static final int H = 3 * CELL_H + 2 * GAP + 8;

    private RackChannelGrid() {
    }

    static void render(GuiGraphics g, Font font, Theme t, int x, int y, int selected, Side side, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        ThemePaint.panel(g, x, y, W, H, t);
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            int cx = cellX(x, i);
            int cy = cellY(y, i);
            ThemePaint.channelTab(g, font, cx, cy, CELL_W, CELL_H, String.valueOf(i), side.type(i), i == selected,
                    false, NetworkDropdown.hits(mx, my, cx, cy, CELL_W, CELL_H), t);
        }
        g.pose().popPose();
    }

    static int hit(double mx, double my, int x, int y) {
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            if (NetworkDropdown.hits(mx, my, cellX(x, i), cellY(y, i), CELL_W, CELL_H)) {
                return i;
            }
        }
        return -1;
    }

    private static int cellX(int x, int i) {
        return x + 4 + i % 3 * (CELL_W + GAP);
    }

    private static int cellY(int y, int i) {
        return y + 4 + i / 3 * (CELL_H + GAP);
    }
}
