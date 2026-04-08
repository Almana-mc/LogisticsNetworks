package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.menu.MassPlacementMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class MassPlacementScreen extends AbstractContainerScreen<MassPlacementMenu> {

    private static final int GUI_WIDTH = 246;
    private static final int GUI_HEIGHT = 218;

    private static final int COLOR_BG = 0xFF161616;
    private static final int COLOR_PANEL = 0xFF1F1F1F;
    private static final int COLOR_BORDER = 0xFF3A3A3A;
    private static final int COLOR_ACCENT = 0xFF44BB44;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF999999;
    private static final int COLOR_OK = 0xFF44CC44;
    private static final int COLOR_FAIL = 0xFFCC4444;
    private static final int COLOR_BTN_BG = 0xFF2A2A2A;
    private static final int COLOR_BTN_HOVER = 0xFF333333;
    private static final int COLOR_BTN_BORDER = 0xFF4A4A4A;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFBBBBBB;
    private static final int COLOR_DISABLED = 0xFF666666;

    private static final int BTN_H = 16;
    private static final int BTN_PAD = 10;
    private static final int BTN_GAP = 6;

    public MassPlacementScreen(MassPlacementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = 10_000;
        this.titleLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, COLOR_BG);
        graphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, COLOR_BORDER);

        graphics.drawCenteredString(font, Component.translatable("gui.logisticsnetworks.mass_placement"),
                leftPos + GUI_WIDTH / 2, topPos + 8, COLOR_ACCENT);

        int panelX = leftPos + 10;
        int panelY = topPos + 24;
        int panelW = GUI_WIDTH - 20;
        int panelH = 136;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
        graphics.renderOutline(panelX, panelY, panelW, panelH, COLOR_BORDER);

        int textX = panelX + 8;
        int y = panelY + 8;
        int textW = panelW - 16;

        y = drawWrappedLine(graphics,
                Component.translatable("gui.logisticsnetworks.mass_placement.selected", menu.getSelectedCount()),
                textX, y, textW, COLOR_TEXT);
        y = drawWrappedLine(graphics,
                Component.translatable("gui.logisticsnetworks.mass_placement.nodes_required", menu.getNodesRequired()),
                textX, y, textW, COLOR_TEXT);
        y = drawWrappedLine(graphics,
                Component.translatable("gui.logisticsnetworks.mass_placement.upgrades_required",
                        menu.getUpgradesRequired()),
                textX, y, textW, COLOR_TEXT);
        y = drawWrappedLine(graphics,
                Component.translatable("gui.logisticsnetworks.mass_placement.filters_required",
                        menu.getFiltersRequired()),
                textX, y, textW, COLOR_TEXT);

        boolean canPlace = menu.canPlace();
        String mark = canPlace ? "\u2714" : "\u2716";
        Component status = Component.translatable(
                canPlace ? "gui.logisticsnetworks.mass_placement.status_ok"
                        : "gui.logisticsnetworks.mass_placement.status_blocked");
        y = drawWrappedLine(graphics, Component.literal(mark + " ").append(status), textX, y, textW,
                canPlace ? COLOR_OK : COLOR_FAIL);

        y = drawWrappedLine(graphics, Component.translatable("gui.logisticsnetworks.mass_placement.requirements"),
                textX, y + 2, textW, COLOR_MUTED);

        List<MassPlacementMenu.RequirementView> requirementViews = menu.getRequirementViews();
        int maxRequirementLines = 6;

        if (requirementViews.isEmpty()) {
            y = drawWrappedLine(graphics,
                    Component.translatable("gui.logisticsnetworks.mass_placement.requirement_empty"),
                    textX, y, textW, COLOR_MUTED);
        } else {
            int shown = Math.min(maxRequirementLines, requirementViews.size());
            for (int i = 0; i < shown; i++) {
                MassPlacementMenu.RequirementView requirement = requirementViews.get(i);
                String requirementMark = requirement.missing() ? "\u2716" : "\u2714";
                int color = requirement.missing() ? COLOR_FAIL : COLOR_OK;
                Component line = Component.literal(requirementMark + " ")
                        .append(requirement.name().copy())
                        .append(Component.literal(": " + requirement.available() + "/" + requirement.required()));
                y = drawWrappedLine(graphics, line, textX, y, textW, color);
            }

            if (requirementViews.size() > shown) {
                y = drawWrappedLine(graphics,
                        Component.translatable("gui.logisticsnetworks.mass_placement.requirement_more",
                                requirementViews.size() - shown),
                        textX, y, textW, COLOR_MUTED);
            }
        }

        int hintY = panelY + panelH + 6;
        drawWrappedLine(graphics, Component.translatable("gui.logisticsnetworks.mass_placement.hint"),
                textX, hintY, textW, COLOR_MUTED);

        String clearLabel = Component.translatable("gui.logisticsnetworks.mass_placement.clear").getString();
        String placeLabel = Component.translatable("gui.logisticsnetworks.mass_placement.place").getString();
        int clearW = font.width(clearLabel) + BTN_PAD * 2;
        int placeW = font.width(placeLabel) + BTN_PAD * 2;
        int totalW = clearW + BTN_GAP + placeW;
        int startX = leftPos + (GUI_WIDTH - totalW) / 2;
        int btnY = topPos + GUI_HEIGHT - BTN_H - 6;

        drawThemedButton(graphics, startX, btnY, clearW, BTN_H, clearLabel,
                menu.getSelectedCount() > 0, mouseX, mouseY);
        drawThemedButton(graphics, startX + clearW + BTN_GAP, btnY, placeW, BTN_H, placeLabel,
                menu.canPlace(), mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) return super.keyPressed(key, scan, modifiers);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            String clearLabel = Component.translatable("gui.logisticsnetworks.mass_placement.clear").getString();
            String placeLabel = Component.translatable("gui.logisticsnetworks.mass_placement.place").getString();
            int clearW = font.width(clearLabel) + BTN_PAD * 2;
            int placeW = font.width(placeLabel) + BTN_PAD * 2;
            int totalW = clearW + BTN_GAP + placeW;
            int startX = leftPos + (GUI_WIDTH - totalW) / 2;
            int btnY = topPos + GUI_HEIGHT - BTN_H - 6;

            if (menu.getSelectedCount() > 0 && isHoveringAbs(startX, btnY, clearW, BTN_H, mx, my)) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MassPlacementMenu.ID_CLEAR_SELECTION);
                }
                return true;
            }

            if (menu.canPlace() && isHoveringAbs(startX + clearW + BTN_GAP, btnY, placeW, BTN_H, mx, my)) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MassPlacementMenu.ID_PLACE_NODES);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawThemedButton(GuiGraphics g, int x, int y, int w, int h, String label,
                                  boolean enabled, int mx, int my) {
        if (!enabled) {
            g.fill(x, y, x + w, y + h, COLOR_PANEL);
            g.renderOutline(x, y, w, h, COLOR_BORDER);
            g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, COLOR_DISABLED);
            return;
        }
        boolean hovered = isHoveringAbs(x, y, w, h, mx, my);
        g.fill(x, y, x + w, y + h, hovered ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        g.renderOutline(x, y, w, h, hovered ? COLOR_ACCENT : COLOR_BTN_BORDER);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hovered ? COLOR_WHITE : COLOR_GRAY);
    }

    private boolean isHoveringAbs(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int drawWrappedLine(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
        return y + 2;
    }
}
