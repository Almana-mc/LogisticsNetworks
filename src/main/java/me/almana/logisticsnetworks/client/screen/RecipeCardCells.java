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

import java.util.ArrayList;
import java.util.List;

final class RecipeCardCells {

    static final int COLOR_SLOT = 0xFF2A2A2A;
    static final int COLOR_SLOT_BORDER = 0xFF444444;
    static final int COLOR_BTN = 0xFF2E2E2E;
    static final int COLOR_BTN_ON = 0xFF2E5E2E;
    static final int COLOR_TEXT = 0xFFE0E0E0;
    static final int COLOR_MUTED = 0xFF999999;
    private static final int COLOR_TAG = 0xFFFFD24A;
    private static final int COLOR_SPLIT = 0xFF2E4A6E;

    private static final int GRID_X = 8;
    private static final int GRID_Y = 40;
    private static final int CELL_W = 88;
    private static final int CELL_H = 20;
    static final int TAG_X = 52;
    static final int SLOTS_X = 70;
    static final int ITEM_BOX_W = 26;
    static final int AMOUNT_BOX_W = 42;
    static final int SLOTS_BOX_W = 44;

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

    static int itemButtonAt(int x, int y, List<RecipeCardData.Ingredient> list, int offset) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).kind() == RecipeCardData.Kind.ITEM
                    && inside(x, y, cellX(i) + offset, cellY(i) + 2, 14, 14)) {
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
        List<Component> lines = new ArrayList<>();
        lines.add(ingredient.label());
        if (!ingredient.slots().isEmpty()) lines.add(ingredient.slotsLabel().withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.logisticsnetworks.recipe_card.remove_hint")
                .withStyle(ChatFormatting.GRAY));
        lines.add(hint);
        return lines;
    }

    static List<Component> slotsTooltip(RecipeCardData.Ingredient ingredient) {
        return List.of(ingredient.slotsLabel(),
                Component.translatable("gui.logisticsnetworks.recipe_card.slots_edit_hint")
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.logisticsnetworks.recipe_card.slots_mode_hint")
                        .withStyle(ChatFormatting.GRAY));
    }

    static EditBox slotsBox(Font font) {
        EditBox box = new EditBox(font, 0, 0, SLOTS_BOX_W, 10, Component.empty());
        box.setBordered(false);
        box.setTextColor(COLOR_TEXT);
        box.setMaxLength(64);
        box.setFilter(value -> value.matches("[0-9,!\\- ]*"));
        box.visible = false;
        return box;
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

    static void cell(GuiGraphics graphics, Font font, int x, int y, @Nullable RecipeCardData.Ingredient ingredient,
            boolean editing) {
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
        int boxW = editing ? SLOTS_BOX_W : ITEM_BOX_W;
        graphics.fill(x + 18, y + 2, x + 22 + boxW, y + 14, COLOR_SLOT_BORDER);
        graphics.fill(x + 19, y + 3, x + 21 + boxW, y + 13, COLOR_SLOT);
        if (!editing) button(graphics, font, x + TAG_X, y, COLOR_BTN, "#");
        button(graphics, font, x + SLOTS_X, y, ingredient.slots().isEmpty() ? COLOR_BTN
                : ingredient.split() ? COLOR_SPLIT : COLOR_BTN_ON, "S");
    }

    private static void button(GuiGraphics graphics, Font font, int x, int y, int fill, String label) {
        graphics.fill(x, y + 1, x + 14, y + 15, COLOR_SLOT_BORDER);
        graphics.fill(x + 1, y + 2, x + 13, y + 14, fill);
        graphics.drawCenteredString(font, label, x + 7, y + 4, COLOR_TEXT);
    }
}
