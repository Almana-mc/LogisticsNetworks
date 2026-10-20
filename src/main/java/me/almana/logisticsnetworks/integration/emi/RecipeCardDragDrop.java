package me.almana.logisticsnetworks.integration.emi;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import me.almana.logisticsnetworks.client.screen.RecipeCardScreen;
import me.almana.logisticsnetworks.component.RecipeCardData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

final class RecipeCardDragDrop implements EmiDragDropHandler<RecipeCardScreen> {

    @Override
    public boolean dropStack(RecipeCardScreen screen, EmiIngredient ingredient, int x, int y) {
        RecipeCardData.Ingredient value = value(ingredient);
        if (value == null) return false;
        for (int i = 0; i < screen.droppableCells(); i++) {
            if (screen.cellArea(i).contains(x, y)) {
                screen.dropResource(i, value);
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(RecipeCardScreen screen, EmiIngredient ingredient, GuiGraphics graphics,
                       int mouseX, int mouseY, float delta) {
        if (value(ingredient) == null) return;
        for (int i = 0; i < screen.droppableCells(); i++) {
            Rect2i area = screen.cellArea(i);
            graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(),
                    area.getY() + area.getHeight(), 0x8822BB33);
        }
    }

    @Nullable
    private static RecipeCardData.Ingredient value(EmiIngredient ingredient) {
        return ingredient.getEmiStacks().size() == 1
                ? RecipeCardEmiHandler.of(ingredient.getEmiStacks().getFirst(), ingredient.getAmount()) : null;
    }
}
