package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.FilterResourceRenderer;
import me.almana.logisticsnetworks.component.RecipeCardData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

final class RecipeCardCells {

    static final int COLOR_SLOT = 0xFF2A2A2A;
    static final int COLOR_SLOT_BORDER = 0xFF444444;
    static final int COLOR_BTN = 0xFF2E2E2E;
    static final int COLOR_TEXT = 0xFFE0E0E0;
    static final int COLOR_MUTED = 0xFF999999;
    private static final int COLOR_TAG = 0xFFFFD24A;

    private static final int GRID_X = 8;
    private static final int GRID_Y = 40;
    private static final int CELL_W = 84;
    private static final int CELL_H = 20;
    static final int TAG_X = 52;
    static final int ITEM_BOX_W = 26;
    static final int AMOUNT_BOX_W = 40;

    private RecipeCardCells() {
    }

    static int cellX(int index) {
        return GRID_X + (index % 2) * CELL_W;
    }

    static int cellY(int index) {
        return GRID_Y + (index / 2) * CELL_H;
    }

    static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT);
    }

    static void icon(GuiGraphics graphics, RecipeCardData.Ingredient ingredient, int x, int y) {
        switch (ingredient.kind()) {
            case FLUID -> FilterResourceRenderer.renderFluid(graphics, ingredient.fluidStack(1000), x, y);
            case CHEMICAL -> FilterResourceRenderer.renderChemical(graphics, ingredient.chemical().orElseThrow(), x, y);
            default -> graphics.renderItem(ingredient.icon(), x, y);
        }
    }

    static void cell(GuiGraphics graphics, Font font, int x, int y, @Nullable RecipeCardData.Ingredient ingredient) {
        slot(graphics, x, y);
        if (ingredient == null) return;
        icon(graphics, ingredient, x, y);
        if (ingredient.kind() != RecipeCardData.Kind.ITEM) {
            graphics.fill(x + 18, y + 2, x + 22 + AMOUNT_BOX_W, y + 14, COLOR_SLOT_BORDER);
            graphics.fill(x + 19, y + 3, x + 21 + AMOUNT_BOX_W, y + 13, COLOR_SLOT);
            graphics.drawString(font, "mB", x + 24 + AMOUNT_BOX_W, y + 4, COLOR_MUTED, false);
            return;
        }
        if (ingredient.tag().isPresent()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, "#", x + 11, y + 8, COLOR_TAG, true);
            graphics.pose().popPose();
        }
        graphics.fill(x + 18, y + 2, x + 48, y + 14, COLOR_SLOT_BORDER);
        graphics.fill(x + 19, y + 3, x + 47, y + 13, COLOR_SLOT);
        graphics.fill(x + TAG_X, y + 1, x + TAG_X + 14, y + 15, COLOR_SLOT_BORDER);
        graphics.fill(x + TAG_X + 1, y + 2, x + TAG_X + 13, y + 14, COLOR_BTN);
        graphics.drawCenteredString(font, "#", x + TAG_X + 7, y + 4, COLOR_TEXT);
    }
}
