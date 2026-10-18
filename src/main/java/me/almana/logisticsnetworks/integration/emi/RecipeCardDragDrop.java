package me.almana.logisticsnetworks.integration.emi;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import me.almana.logisticsnetworks.client.screen.RecipeCardScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

final class RecipeCardDragDrop implements EmiDragDropHandler<RecipeCardScreen> {

    @Override
    public boolean dropStack(RecipeCardScreen screen, EmiIngredient ingredient, int x, int y) {
        ItemStack stack = item(ingredient);
        if (stack.isEmpty()) return false;
        for (int i = 0; i < screen.droppableCells(); i++) {
            if (screen.cellArea(i).contains(x, y)) {
                screen.dropIngredient(i, stack);
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(RecipeCardScreen screen, EmiIngredient ingredient, GuiGraphics graphics,
                       int mouseX, int mouseY, float delta) {
        if (item(ingredient).isEmpty()) return;
        for (int i = 0; i < screen.droppableCells(); i++) {
            Rect2i area = screen.cellArea(i);
            graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(),
                    area.getY() + area.getHeight(), 0x8822BB33);
        }
    }

    private static ItemStack item(EmiIngredient ingredient) {
        return ingredient.getEmiStacks().size() == 1
                ? ingredient.getEmiStacks().getFirst().getItemStack() : ItemStack.EMPTY;
    }
}
