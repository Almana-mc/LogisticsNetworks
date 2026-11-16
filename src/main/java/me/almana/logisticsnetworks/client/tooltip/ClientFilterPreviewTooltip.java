package me.almana.logisticsnetworks.client.tooltip;

import me.almana.logisticsnetworks.client.FilterResourceRenderer;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

public final class ClientFilterPreviewTooltip implements ClientTooltipComponent {

    private static final int SLOT_SIZE = 18;
    private static final int MAX_COLUMNS = 9;

    private final FilterPreviewTooltip preview;

    public ClientFilterPreviewTooltip(FilterPreviewTooltip preview) {
        this.preview = preview;
    }

    @Override
    public int getHeight() {
        return rows() * SLOT_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return columns() * SLOT_SIZE;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        Theme theme = ThemeState.active();
        for (int index = 0; index < preview.entries().size(); index++) {
            int slotX = x + index % columns() * SLOT_SIZE;
            int slotY = y + index / columns() * SLOT_SIZE;
            ThemePaint.slot(graphics, slotX, slotY, SLOT_SIZE, theme);
            renderEntry(font, graphics, preview.entries().get(index), slotX + 1, slotY + 1, theme);
        }
    }

    private void renderEntry(Font font, GuiGraphics graphics, FilterPreviewTooltip.Entry entry,
            int x, int y, Theme theme) {
        boolean rendered = switch (entry.kind()) {
            case ITEM -> {
                graphics.renderItem(entry.item(), x, y);
                yield !entry.item().isEmpty();
            }
            case FLUID -> renderFluid(graphics, entry.id(), x, y);
            case CHEMICAL -> FilterResourceRenderer.renderChemical(graphics, entry.id(), x, y);
            case TAG -> {
                drawMarker(font, graphics, "#", x, y, theme.accent());
                yield true;
            }
            case RULE -> false;
        };
        if (!rendered) {
            drawMarker(font, graphics, "?", x, y, theme.textMuted());
        }
    }

    private boolean renderFluid(GuiGraphics graphics, String fluidId, int x, int y) {
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) {
            return false;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return FilterResourceRenderer.renderFluid(graphics, new FluidStack(fluid, 1000), x, y);
    }

    private void drawMarker(Font font, GuiGraphics graphics, String marker, int x, int y, int color) {
        graphics.drawString(font, marker, x + (16 - font.width(marker)) / 2, y + 4, color, true);
    }

    private int columns() {
        return Math.min(MAX_COLUMNS, Math.max(1, preview.entries().size()));
    }

    private int rows() {
        return (preview.entries().size() + columns() - 1) / columns();
    }
}
