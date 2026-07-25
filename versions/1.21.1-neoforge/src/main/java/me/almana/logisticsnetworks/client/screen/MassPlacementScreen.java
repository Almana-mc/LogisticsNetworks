package me.almana.logisticsnetworks.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.menu.MassPlacementMenu;
import me.almana.logisticsnetworks.network.SyncMassPlacementChoicesPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class MassPlacementScreen extends AbstractContainerScreen<MassPlacementMenu> {

    private static final int GUI_WIDTH = 246;
    private static final int GUI_HEIGHT = 262;

    private Theme theme() { return ThemeState.active(); }
    private int cBg() { return theme().bg(); }
    private int cPanel() { return theme().surface(); }
    private int cBorder() { return theme().border(); }
    private int cAccent() { return theme().accent(); }
    private int cText() { return theme().text(); }
    private int cMuted() { return theme().textMuted(); }
    private int cBtnBg() { return theme().surface2(); }
    private int cBtnHover() { return ThemePaint.brighten(theme().surface2(), 0x10); }
    private int cBtnBorder() { return theme().borderStrong(); }

    private static final int BTN_H = 16;
    private static final int BTN_PAD = 10;
    private static final int BTN_GAP = 6;
    private static final int CHOICE_ROW_H = 14;
    private static final int CHOICE_ROW_GAP = 2;
    private static final int MAX_CHOICE_ROWS = 3;
    private static final int SCROLLBAR_W = 3;
    private static final int PANEL_X_OFFSET = 10;
    private static final int PANEL_Y_OFFSET = 24;
    private static final int PANEL_W = GUI_WIDTH - PANEL_X_OFFSET * 2;
    private static final int PANEL_H = 176;

    private List<SyncMassPlacementChoicesPayload.BlockChoice> blockChoices = List.of();
    private int choiceScrollOffset;
    private int maxNodes = 2048;

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
        graphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, cBg());
        graphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, cBorder());

        graphics.drawCenteredString(font, Component.translatable("gui.logisticsnetworks.mass_placement"),
                leftPos + GUI_WIDTH / 2, topPos + 8, cAccent());

        int panelX = leftPos + PANEL_X_OFFSET;
        int panelY = topPos + PANEL_Y_OFFSET;
        int panelW = PANEL_W;
        int panelH = PANEL_H;
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, cPanel());
        graphics.renderOutline(panelX, panelY, panelW, panelH, cBorder());

        int textX = panelX + 8;
        int y = panelY + 8;
        int textW = panelW - 16;

        y = drawWrappedLine(graphics,
                Component.translatable("gui.logisticsnetworks.mass_placement.selected", menu.getSelectedCount()),
                textX, y, textW, cText());
        y = drawWrappedLine(graphics,
                Component.translatable("gui.logisticsnetworks.mass_placement.nodes_required", menu.getNodesRequired()),
                textX, y, textW, cText());
        y = drawWrappedLine(graphics,
                Component.translatable("gui.logisticsnetworks.mass_placement.upgrades_required",
                        menu.getUpgradesRequired()),
                textX, y, textW, cText());
        y = drawWrappedLine(graphics,
                Component.translatable("gui.logisticsnetworks.mass_placement.filters_required",
                        menu.getFiltersRequired()),
                textX, y, textW, cText());

        boolean canPlace = menu.canPlace();
        String mark = canPlace ? "\u2714" : "\u2716";
        Component status = Component.translatable(
                canPlace ? "gui.logisticsnetworks.mass_placement.status_ok"
                        : "gui.logisticsnetworks.mass_placement.status_blocked");
        y = drawWrappedLine(graphics, Component.literal(mark + " ").append(status), textX, y, textW,
                canPlace ? theme().accent() : theme().danger());

        int choicesTitleY = panelY + 67;
        drawChoiceHeader(graphics, textX, choicesTitleY, textW);
        drawBlockChoices(graphics, textX, getChoiceListY(), textW, mouseX, mouseY);

        int requirementsTitleY = panelY + 130;
        drawWrappedLine(graphics, Component.translatable("gui.logisticsnetworks.mass_placement.requirements"),
                textX, requirementsTitleY, textW, cMuted());

        List<MassPlacementMenu.RequirementView> requirementViews = menu.getRequirementViews();
        int maxRequirementLines = 2;
        int requirementY = panelY + 143;

        if (requirementViews.isEmpty()) {
            drawWrappedLine(graphics,
                    Component.translatable("gui.logisticsnetworks.mass_placement.requirement_empty"),
                    textX, requirementY, textW, cMuted());
        } else {
            int shown = Math.min(maxRequirementLines, requirementViews.size());
            for (int i = 0; i < shown; i++) {
                MassPlacementMenu.RequirementView requirement = requirementViews.get(i);
                String requirementMark = requirement.missing() ? "\u2716" : "\u2714";
                int color = requirement.missing() ? theme().danger() : theme().accent();
                Component line = Component.literal(requirementMark + " ")
                        .append(requirement.name().copy())
                        .append(Component.literal(": " + requirement.available() + "/" + requirement.required()));
                if (requirement.fromAE2() > 0) {
                    line = line.copy().append(Component.literal(" +" + requirement.fromAE2() + " ME")
                            .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
                }
                graphics.drawString(font, font.plainSubstrByWidth(line.getString(), textW), textX,
                        requirementY + i * font.lineHeight, color, false);
            }

            if (requirementViews.size() > shown) {
                graphics.drawString(font, Component.translatable("gui.logisticsnetworks.mass_placement.requirement_more",
                        requirementViews.size() - shown), textX,
                        requirementY + shown * font.lineHeight + 1, cMuted(), false);
            }
        }

        int hintY = panelY + panelH + 6;
        drawWrappedLine(graphics, Component.translatable("gui.logisticsnetworks.mass_placement.hint"),
                textX, hintY, textW, cMuted());

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
            int choiceIndex = hoveredChoiceIndex(mx, my);
            if (choiceIndex >= 0 && choiceIndex < blockChoices.size()) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            MassPlacementMenu.ID_SELECT_BLOCK_BASE + choiceIndex);
                }
                return true;
            }

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

    public void receiveBlockChoices(List<SyncMassPlacementChoicesPayload.BlockChoice> choices, int maxNodes) {
        this.blockChoices = choices == null ? List.of() : List.copyOf(choices);
        this.maxNodes = maxNodes;
        clampChoiceScroll();
    }

    public boolean hasContainerId(int containerId) {
        return menu.containerId == containerId;
    }

    private int drawBlockChoices(GuiGraphics g, int x, int y, int width, int mx, int my) {
        if (blockChoices.isEmpty()) {
            return drawWrappedLine(g, Component.translatable("gui.logisticsnetworks.mass_placement.block_choices_empty"),
                    x, y, width, cMuted());
        }

        clampChoiceScroll();
        int shown = Math.min(MAX_CHOICE_ROWS, blockChoices.size() - choiceScrollOffset);
        int rowWidth = getMaxChoiceScroll() > 0 ? width - SCROLLBAR_W - 4 : width;
        for (int i = 0; i < shown; i++) {
            SyncMassPlacementChoicesPayload.BlockChoice choice = blockChoices.get(choiceScrollOffset + i);
            boolean hovered = isHoveringAbs(x, y, rowWidth, CHOICE_ROW_H, mx, my);
            boolean selected = choice.selected();
            int border = selected ? cAccent() : hovered ? cBtnBorder() : cBorder();
            int fill = hovered ? cBtnHover() : cBtnBg();
            g.fill(x, y, x + rowWidth, y + CHOICE_ROW_H, fill);
            g.renderOutline(x, y, rowWidth, CHOICE_ROW_H, border);

            String count = choice.targetCount() > maxNodes ? maxNodes + "+" : String.valueOf(choice.targetCount());
            String label = (selected ? "> " : "  ") + choice.name() + " (" + count + ")";
            g.drawString(font, font.plainSubstrByWidth(label, rowWidth - 8), x + 4, y + 3,
                    selected ? cText() : cMuted(), false);
            y += CHOICE_ROW_H + CHOICE_ROW_GAP;
        }

        drawChoiceScrollbar(g, x, width);
        return y;
    }

    private int hoveredChoiceIndex(double mx, double my) {
        int panelX = leftPos + PANEL_X_OFFSET;
        int panelW = PANEL_W;
        int textX = panelX + 8;
        int y = getChoiceListY();
        int textW = panelW - 16;
        int rowW = getMaxChoiceScroll() > 0 ? textW - SCROLLBAR_W - 4 : textW;

        int shown = Math.min(MAX_CHOICE_ROWS, blockChoices.size() - choiceScrollOffset);
        for (int i = 0; i < shown; i++) {
            if (isHoveringAbs(textX, y, rowW, CHOICE_ROW_H, mx, my)) {
                return choiceScrollOffset + i;
            }
            y += CHOICE_ROW_H + CHOICE_ROW_GAP;
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = getMaxChoiceScroll();
        if (maxScroll > 0 && isHoveringAbs(leftPos + PANEL_X_OFFSET + 8, getChoiceListY(),
                PANEL_W - 16, MAX_CHOICE_ROWS * CHOICE_ROW_H + (MAX_CHOICE_ROWS - 1) * CHOICE_ROW_GAP,
                mouseX, mouseY)) {
            choiceScrollOffset = Math.max(0, Math.min(choiceScrollOffset - (int) scrollY, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawChoiceHeader(GuiGraphics g, int x, int y, int width) {
        g.drawString(font, Component.translatable("gui.logisticsnetworks.mass_placement.block_choices"),
                x, y, cMuted(), false);
        int maxScroll = getMaxChoiceScroll();
        if (maxScroll <= 0) {
            return;
        }

        int first = choiceScrollOffset + 1;
        int last = Math.min(choiceScrollOffset + MAX_CHOICE_ROWS, blockChoices.size());
        String range = first + "-" + last + "/" + blockChoices.size();
        g.drawString(font, range, x + width - font.width(range), y, cMuted(), false);
    }

    private int getChoiceListY() {
        return topPos + PANEL_Y_OFFSET + 80;
    }

    private int getMaxChoiceScroll() {
        return Math.max(0, blockChoices.size() - MAX_CHOICE_ROWS);
    }

    private void clampChoiceScroll() {
        choiceScrollOffset = Math.max(0, Math.min(choiceScrollOffset, getMaxChoiceScroll()));
    }

    private void drawChoiceScrollbar(GuiGraphics g, int x, int width) {
        int maxScroll = getMaxChoiceScroll();
        if (maxScroll <= 0) {
            return;
        }

        int trackX = x + width - SCROLLBAR_W;
        int trackY = getChoiceListY();
        int trackH = MAX_CHOICE_ROWS * CHOICE_ROW_H + (MAX_CHOICE_ROWS - 1) * CHOICE_ROW_GAP;
        g.fill(trackX, trackY, trackX + SCROLLBAR_W, trackY + trackH, cBorder());

        int thumbH = Math.max(8, trackH * MAX_CHOICE_ROWS / blockChoices.size());
        int thumbTravel = trackH - thumbH;
        int thumbY = trackY + (thumbTravel * choiceScrollOffset) / maxScroll;
        g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, cAccent());
    }

    private void drawThemedButton(GuiGraphics g, int x, int y, int w, int h, String label,
                                  boolean enabled, int mx, int my) {
        if (!enabled) {
            g.fill(x, y, x + w, y + h, cPanel());
            g.renderOutline(x, y, w, h, cBorder());
            g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, theme().textSubtle());
            return;
        }
        boolean hovered = isHoveringAbs(x, y, w, h, mx, my);
        g.fill(x, y, x + w, y + h, hovered ? cBtnHover() : cBtnBg());
        g.renderOutline(x, y, w, h, hovered ? cAccent() : cBtnBorder());
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hovered ? cText() : cMuted());
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
