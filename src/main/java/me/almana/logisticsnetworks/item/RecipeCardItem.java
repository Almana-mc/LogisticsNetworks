package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.component.RecipeCardData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class RecipeCardItem extends Item {

    public RecipeCardItem(Properties properties) {
        super(properties);
    }

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof RecipeCardItem;
    }

    public static boolean anyIn(ItemStack[] filters) {
        for (ItemStack filter : filters) {
            if (is(filter)) return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        RecipeCardData data = RecipeCardData.of(stack);
        if (data.ingredients().isEmpty()) {
            lines.add(Component.translatable("tooltip.logisticsnetworks.recipe_card.blank")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        data.output().ifPresent(output -> lines.add(Component.translatable(
                "tooltip.logisticsnetworks.recipe_card.output", output.toStack().getHoverName())
                .withStyle(ChatFormatting.GOLD)));
        for (RecipeCardData.Ingredient ingredient : data.ingredients()) {
            lines.add(Component.literal(ingredient.count() + "× ").append(ingredient.label())
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(data.blocking()
                ? "tooltip.logisticsnetworks.recipe_card.blocking_on"
                : "tooltip.logisticsnetworks.recipe_card.blocking_off").withStyle(ChatFormatting.DARK_GRAY));
    }
}
