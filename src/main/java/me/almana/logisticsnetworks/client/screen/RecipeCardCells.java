package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.FilterResourceRenderer;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    static final int AMOUNT_BOX_W = 42;

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
        boolean drawn = switch (ingredient.kind()) {
            case FLUID -> FilterResourceRenderer.renderFluid(graphics, ingredient.fluidStack(FluidType.BUCKET_VOLUME), x, y);
            case CHEMICAL -> FilterResourceRenderer.renderChemical(graphics, ingredient.chemical().orElseThrow(), x, y);
            default -> false;
        };
        if (!drawn) graphics.renderItem(ingredient.icon(), x, y);
    }

    static int slotAt(int x, int y, int cells) {
        for (int i = 0; i < cells; i++) {
            if (inside(x, y, cellX(i), cellY(i) + 1, 16, 16)) return i;
        }
        return -1;
    }

    static int tagAt(int x, int y, List<RecipeCardData.Ingredient> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).kind() == RecipeCardData.Kind.ITEM
                    && inside(x, y, cellX(i) + TAG_X, cellY(i) + 2, 14, 14)) {
                return i;
            }
        }
        return -1;
    }

    static List<Component> tooltip(@Nullable RecipeCardData.Ingredient ingredient) {
        Component hint = Component.translatable("gui.logisticsnetworks.recipe_card.contents_hint",
                ClientControls.MODIFIER_1.getTranslatedKeyMessage())
                .withStyle(ChatFormatting.GRAY);
        if (ingredient == null) return List.of(hint);
        return List.of(ingredient.label(), Component.translatable("gui.logisticsnetworks.recipe_card.remove_hint")
                .withStyle(ChatFormatting.GRAY), hint);
    }

    static EditBox countBox(Font font, int x, int y) {
        EditBox box = new EditBox(font, x + 21, y + 5, ITEM_BOX_W, 10, Component.empty());
        box.setBordered(false);
        box.setTextColor(COLOR_TEXT);
        box.setFilter(value -> value.isEmpty() || value.matches("\\d+"));
        return box;
    }

    static void fit(EditBox box, RecipeCardData.Ingredient ingredient) {
        boolean item = ingredient.kind() == RecipeCardData.Kind.ITEM;
        box.setWidth(item ? ITEM_BOX_W : AMOUNT_BOX_W);
        box.setMaxLength(item ? 4 : 7);
    }

    static RecipeCardData.Ingredient contents(ItemStack stack) {
        FluidStack fluid = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
        String chemical = MekanismCompat.getChemicalIdFromItem(stack);
        if (!fluid.isEmpty()) return RecipeCardData.Ingredient.ofFluid(fluid);
        if (chemical != null) return RecipeCardData.Ingredient.ofChemical(chemical, FluidType.BUCKET_VOLUME);
        return RecipeCardData.Ingredient.ofItem(stack, stack.getCount());
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
        graphics.fill(x + 18, y + 2, x + 22 + ITEM_BOX_W, y + 14, COLOR_SLOT_BORDER);
        graphics.fill(x + 19, y + 3, x + 21 + ITEM_BOX_W, y + 13, COLOR_SLOT);
        graphics.fill(x + TAG_X, y + 1, x + TAG_X + 14, y + 15, COLOR_SLOT_BORDER);
        graphics.fill(x + TAG_X + 1, y + 2, x + TAG_X + 13, y + 14, COLOR_BTN);
        graphics.drawCenteredString(font, "#", x + TAG_X + 7, y + 4, COLOR_TEXT);
    }
}
