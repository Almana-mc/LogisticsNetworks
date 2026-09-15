package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.SetWrenchModePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WrenchModeScreen extends Screen {
    private static final int INNER_RADIUS = 24;
    private static final int OUTER_RADIUS = 102;
    private final InteractionHand hand;
    private final WrenchItem.Mode active;

    public WrenchModeScreen(InteractionHand hand, WrenchItem.Mode active) {
        super(Component.translatable("gui.logisticsnetworks.wrench.modes.title"));
        this.hand = hand;
        this.active = active;
    }

    @Override
    public void tick() {
        if (!minecraft.isWindowActive() || minecraft.player == null
                || !(minecraft.player.getItemInHand(hand).getItem() instanceof WrenchItem)) {
            onClose();
        } else if (!ClientControls.wrenchModesDown()) {
            int selected = sector(ClientControls.cursorX(minecraft) - width / 2.0,
                    ClientControls.cursorY(minecraft) - height / 2.0);
            if (selected >= 0) PacketDistributor.sendToServer(new SetWrenchModePayload(hand, WrenchItem.Mode.values()[selected]));
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static int sector(double x, double y) {
        if (x * x + y * y < INNER_RADIUS * INNER_RADIUS) return -1;
        return (int) ((Math.toDegrees(Math.atan2(y, x)) + 510) % 360 / 120);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var theme = ThemeState.active();
        int selected = sector(mouseX - width / 2.0, mouseY - height / 2.0);
        ThemePaint.drawCentered(graphics, font, title, width / 2, height / 2 - 116, theme.text());
        for (WrenchItem.Mode mode : WrenchItem.Mode.values()) {
            int index = mode.ordinal();
            Component label = WrenchItem.getModeDisplayName(mode);
            int color = label.getStyle().getColor().getValue();
            int shade = index == selected ? (color & 0xFEFEFE) >> 1 : (color & 0xFCFCFC) >> 2;
            drawSector(graphics, index, 0xEE000000 | shade);
            double angle = Math.toRadians(-90 + index * 120);
            int x = width / 2 + (int) (Math.cos(angle) * 66);
            int y = height / 2 + (int) (Math.sin(angle) * 66);
            ThemePaint.drawCentered(graphics, font, label.copy().withStyle(ChatFormatting.WHITE), x, y - 4, theme.text());
            if (mode == active) ThemePaint.drawCentered(graphics, font,
                    Component.translatable("gui.logisticsnetworks.wrench.modes.active"), x, y + 8, theme.text());
        }
        ThemePaint.drawCentered(graphics, font, Component.translatable("gui.cancel"),
                width / 2, height / 2 - 4, theme.textMuted());
        ThemePaint.drawCentered(graphics, font, Component.translatable("gui.logisticsnetworks.wrench.modes.hint",
                ClientControls.WRENCH_MODES.getTranslatedKeyMessage()), width / 2, height / 2 + 110, theme.textMuted());
    }

    private void drawSector(GuiGraphics graphics, int sector, int color) {
        var vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        var pose = graphics.pose().last().pose();
        float x = width / 2f;
        float y = height / 2f;
        for (int step = 0; step < 30; step++) {
            double a = Math.toRadians(-149 + sector * 120 + step * 118.0 / 30);
            double b = Math.toRadians(-149 + sector * 120 + (step + 1) * 118.0 / 30);
            vertices.addVertex(pose, x + (float) Math.cos(a) * INNER_RADIUS, y + (float) Math.sin(a) * INNER_RADIUS, 0).setColor(color);
            vertices.addVertex(pose, x + (float) Math.cos(b) * INNER_RADIUS, y + (float) Math.sin(b) * INNER_RADIUS, 0).setColor(color);
            vertices.addVertex(pose, x + (float) Math.cos(b) * OUTER_RADIUS, y + (float) Math.sin(b) * OUTER_RADIUS, 0).setColor(color);
            vertices.addVertex(pose, x + (float) Math.cos(a) * OUTER_RADIUS, y + (float) Math.sin(a) * OUTER_RADIUS, 0).setColor(color);
        }
    }
}
