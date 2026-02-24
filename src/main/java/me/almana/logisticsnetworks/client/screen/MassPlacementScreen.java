package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.menu.MassPlacementMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    private Button placeButton;

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

        placeButton = Button.builder(Component.translatable("gui.logisticsnetworks.mass_placement.place"), btn -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MassPlacementMenu.ID_PLACE_NODES);
            }
        }).bounds(leftPos + (GUI_WIDTH - 118) / 2, topPos + GUI_HEIGHT - 24, 118, 20).build();

        addRenderableWidget(placeButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (placeButton != null) {
            placeButton.active = menu.canPlace();
        }
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
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private int drawWrappedLine(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
        return y + 2;
    }
}
