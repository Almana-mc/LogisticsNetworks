package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.data.NetworkColors;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.SetWrenchColorsPayload;
import me.almana.logisticsnetworks.client.LegacyScreen;
//? if >=26
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class WrenchColorScreen extends LegacyScreen {

    private static final Identifier CASE_TEXTURE = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID,
            "textures/item/wrench_case.png");
    private static final Identifier SCREEN_TEXTURE = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID,
            "textures/item/wrench_screen.png");

    private static final int SQ = 100;
    private static final int SQ_H = 76;
    private static final int HUE_H = 10;
    private static final int PAD = 10;
    private static final int PREVIEW = 96;
    private static final int TAB_H = 14;
    private static final int SV_COLS = 25;
    private static final int SV_ROWS = 19;
    private static final int HUE_CELLS = 50;
    private static final int GUI_W = PAD + SQ + 14 + PREVIEW + PAD;

    private static final int TAB_CASE = 0;
    private static final int TAB_SCREEN = 1;

    private final InteractionHand hand;
    private final int[] colors = new int[2];

    private int x;
    private int y;
    private int tab = TAB_CASE;
    private float hue;
    private float sat;
    private float val;
    private String hex;
    private boolean hexFocused;
    private int drag = -1;

    public WrenchColorScreen(ItemStack wrenchStack, InteractionHand hand) {
        super(Component.translatable("gui.logisticsnetworks.wrench.colors.title"));
        this.hand = hand;
        this.colors[TAB_CASE] = WrenchItem.getCaseColor(wrenchStack);
        this.colors[TAB_SCREEN] = WrenchItem.getScreenColor(wrenchStack);
        loadColor(colors[TAB_CASE]);
    }

    @Override
    protected void init() {
        x = (width - GUI_W) / 2;
        y = (height - guiHeight()) / 2;
    }

    private int guiHeight() {
        return PAD + 10 + TAB_H + 8 + SQ_H + 6 + HUE_H + 8 + 12 + 8 + 12 + PAD;
    }

    private int current() {
        return NetworkColors.hsvToRgb(hue, sat, val);
    }

    private void loadColor(int rgb) {
        float[] hsv = NetworkColors.rgbToHsv(rgb);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        hex = NetworkColors.toHex(rgb);
    }

    private void commitCurrent() {
        colors[tab] = current();
    }

    private int tabY() { return y + PAD + 10; }
    private int svX() { return x + PAD; }
    private int svY() { return tabY() + TAB_H + 8; }
    private int hueY() { return svY() + SQ_H + 6; }
    private int rowY() { return hueY() + HUE_H + 8; }
    private int btnY() { return rowY() + 12 + 8; }
    private int previewX() { return x + PAD + SQ + 14; }
    private int previewY() { return svY() + (SQ_H + 6 + HUE_H - PREVIEW) / 2 + PREVIEW / 8; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderSuper(g, mx, my, pt);
        Theme t = ThemeState.active();

        ThemePaint.window(g, x, y, GUI_W, guiHeight(), t);
        ThemePaint.drawCentered(g, font, title, x + GUI_W / 2, y + PAD, t.accent());

        renderTabs(g, mx, my, t);
        renderSvSquare(g);
        renderHueStrip(g);
        renderHexRow(g, t);
        renderButtons(g, mx, my, t);
        renderPreview(g, t);
    }

    private void renderTabs(GuiGraphics g, int mx, int my, Theme t) {
        int half = (SQ - 4) / 2;
        String[] labels = {
                tr("gui.logisticsnetworks.wrench.colors.case"),
                tr("gui.logisticsnetworks.wrench.colors.screen") };
        for (int i = 0; i < 2; i++) {
            int tx = svX() + i * (half + 4);
            boolean hovered = inRect(mx, my, tx, tabY(), half, TAB_H);
            ThemePaint.button(g, font, tx, tabY(), half, TAB_H, labels[i], hovered || tab == i, t);
            if (tab == i) {
                g.renderOutline(tx, tabY(), half, TAB_H, t.accent());
            }
        }
    }

    private void renderSvSquare(GuiGraphics g) {
        int sx = svX();
        int sy = svY();
        float cellW = SQ / (float) SV_COLS;
        float cellH = SQ_H / (float) SV_ROWS;
        for (int c = 0; c < SV_COLS; c++) {
            for (int r = 0; r < SV_ROWS; r++) {
                float s = c / (float) (SV_COLS - 1);
                float v = 1f - r / (float) (SV_ROWS - 1);
                int color = 0xFF000000 | NetworkColors.hsvToRgb(hue, s, v);
                int x0 = sx + Math.round(c * cellW);
                int y0 = sy + Math.round(r * cellH);
                int x1 = sx + Math.round((c + 1) * cellW);
                int y1 = sy + Math.round((r + 1) * cellH);
                g.fill(x0, y0, x1, y1, color);
            }
        }
        int cx = sx + Math.round(sat * SQ);
        int cy = sy + Math.round((1f - val) * SQ_H);
        g.renderOutline(cx - 2, cy - 2, 4, 4, 0xFFFFFFFF);
        g.renderOutline(cx - 1, cy - 1, 2, 2, 0xFF000000);
    }

    private void renderHueStrip(GuiGraphics g) {
        int hx = svX();
        int hy = hueY();
        float cellW = SQ / (float) HUE_CELLS;
        for (int i = 0; i < HUE_CELLS; i++) {
            int color = 0xFF000000 | NetworkColors.hsvToRgb(i / (float) (HUE_CELLS - 1), 1f, 1f);
            int x0 = hx + Math.round(i * cellW);
            int x1 = hx + Math.round((i + 1) * cellW);
            g.fill(x0, hy, x1, hy + HUE_H, color);
        }
        int cx = hx + Math.round(hue * SQ);
        g.renderOutline(cx - 1, hy - 1, 3, HUE_H + 2, 0xFFFFFFFF);
    }

    private void renderHexRow(GuiGraphics g, Theme t) {
        int rx = svX();
        int swW = 16;
        g.fill(rx, rowY(), rx + swW, rowY() + 12, 0xFF000000 | current());
        g.renderOutline(rx, rowY(), swW, 12, t.border());

        int hexX = rx + swW + 6;
        int hexW = SQ - swW - 6;
        ThemePaint.sunkPanel(g, hexX, rowY(), hexW, 12, t);
        String shown = "#" + hex + (hexFocused ? "_" : "");
        g.drawString(font, shown, hexX + 4, rowY() + 2, t.text(), false);
    }

    private void renderButtons(GuiGraphics g, int mx, int my, Theme t) {
        int bw = (GUI_W - 2 * PAD - 12) / 3;
        int bx = x + PAD;
        String[] labels = {
                tr("gui.logisticsnetworks.node.color.random"),
                tr("gui.logisticsnetworks.wrench.colors.reset"),
                tr("gui.logisticsnetworks.node.color.apply") };
        for (int i = 0; i < 3; i++) {
            int px = bx + i * (bw + 6);
            ThemePaint.button(g, font, px, btnY(), bw, 12, labels[i], inRect(mx, my, px, btnY(), bw, 12), t);
        }
    }

    private void renderPreview(GuiGraphics g, Theme t) {
        int px = previewX();
        int py = previewY();
        ThemePaint.sunkPanel(g, px - 4, py - 4, PREVIEW + 8, PREVIEW + 8, t);
        //? if <26 {
        /*// pre-26 has no tinted blit, so scale by pose and tint the shader
        int caseColor = tab == TAB_CASE ? current() : colors[TAB_CASE];
        int screenColor = tab == TAB_SCREEN ? current() : colors[TAB_SCREEN];
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(PREVIEW / 16f, PREVIEW / 16f, 1f);
        drawLayer(g, CASE_TEXTURE, caseColor);
        drawLayer(g, SCREEN_TEXTURE, screenColor);
        g.pose().popPose();
        *///?} else {
        int caseColor = 0xFF000000 | (tab == TAB_CASE ? current() : colors[TAB_CASE]);
        int screenColor = 0xFF000000 | (tab == TAB_SCREEN ? current() : colors[TAB_SCREEN]);
        g.raw().blit(RenderPipelines.GUI_TEXTURED, CASE_TEXTURE, px, py, 0f, 0f,
                PREVIEW, PREVIEW, 16, 16, 16, 16, caseColor);
        g.raw().blit(RenderPipelines.GUI_TEXTURED, SCREEN_TEXTURE, px, py, 0f, 0f,
                PREVIEW, PREVIEW, 16, 16, 16, 16, screenColor);
        //?}
    }

    //? if <26 {
    /*private static void drawLayer(GuiGraphics g, Identifier texture, int color) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
        g.blit(texture, 0, 0, 0f, 0f, 16, 16, 16, 16);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
    *///?}

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }

        int half = (SQ - 4) / 2;
        if (inRect(mx, my, svX(), tabY(), half, TAB_H)) {
            switchTab(TAB_CASE);
            return true;
        }
        if (inRect(mx, my, svX() + half + 4, tabY(), half, TAB_H)) {
            switchTab(TAB_SCREEN);
            return true;
        }
        if (inRect(mx, my, svX(), svY(), SQ, SQ_H)) {
            drag = 0;
            updateSv(mx, my);
            hexFocused = false;
            return true;
        }
        if (inRect(mx, my, svX(), hueY(), SQ, HUE_H)) {
            drag = 1;
            updateHue(mx);
            hexFocused = false;
            return true;
        }
        int hexX = svX() + 16 + 6;
        if (inRect(mx, my, hexX, rowY(), SQ - 16 - 6, 12)) {
            hexFocused = true;
            return true;
        }
        int bw = (GUI_W - 2 * PAD - 12) / 3;
        int bx = x + PAD;
        if (inRect(mx, my, bx, btnY(), bw, 12)) {
            loadColor(NetworkColors.randomColor());
            hexFocused = false;
            return true;
        }
        if (inRect(mx, my, bx + bw + 6, btnY(), bw, 12)) {
            commitCurrent();
            colors[TAB_CASE] = WrenchItem.DEFAULT_CASE_COLOR;
            colors[TAB_SCREEN] = WrenchItem.DEFAULT_SCREEN_COLOR;
            loadColor(colors[tab]);
            hexFocused = false;
            return true;
        }
        if (inRect(mx, my, bx + 2 * (bw + 6), btnY(), bw, 12)) {
            apply();
            return true;
        }
        if (!inRect(mx, my, x, y, GUI_W, guiHeight())) {
            onClose();
            return true;
        }
        hexFocused = false;
        return true;
    }

    private void switchTab(int newTab) {
        if (tab == newTab) {
            return;
        }
        commitCurrent();
        tab = newTab;
        loadColor(colors[tab]);
        hexFocused = false;
    }

    private void apply() {
        commitCurrent();
        boolean isDefault = colors[TAB_CASE] == WrenchItem.DEFAULT_CASE_COLOR
                && colors[TAB_SCREEN] == WrenchItem.DEFAULT_SCREEN_COLOR;
        ClientPacketDistributor.sendToServer(new SetWrenchColorsPayload(hand.ordinal(), isDefault,
                colors[TAB_CASE], colors[TAB_SCREEN]));
        onClose();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == 0) {
            updateSv(mx, my);
            return true;
        }
        if (drag == 1) {
            updateHue(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag != -1) {
            drag = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!hexFocused || hex.length() >= 6) {
            return hexFocused;
        }
        if (isHexChar(c)) {
            hex += Character.toUpperCase(c);
            syncFromHex();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (hexFocused) {
            if (key == 259 && !hex.isEmpty()) {
                hex = hex.substring(0, hex.length() - 1);
                syncFromHex();
                return true;
            }
            if (key == 257 || key == 335) {
                hexFocused = false;
                return true;
            }
            if (key == 256) {
                hexFocused = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void syncFromHex() {
        if (hex.length() == 6) {
            float[] hsv = NetworkColors.rgbToHsv(NetworkColors.parseHex(hex, current()));
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
        }
    }

    private void updateSv(double mx, double my) {
        sat = clamp01((float) (mx - svX()) / SQ);
        val = 1f - clamp01((float) (my - svY()) / SQ_H);
        hex = NetworkColors.toHex(current());
    }

    private void updateHue(double mx) {
        hue = clamp01((float) (mx - svX()) / SQ);
        hex = NetworkColors.toHex(current());
    }

    private static boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }
}
