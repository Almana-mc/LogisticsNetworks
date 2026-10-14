package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.menu.RecipeCardMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class RecipeCardItem extends Item {

    public RecipeCardItem(Properties properties) {
        super(properties);
    }

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof RecipeCardItem;
    }

    public static ItemStack stamp(ItemStack card) {
        ItemStack clean = new ItemStack(card.getItem());
        RecipeCardData data = RecipeCardData.of(card).sanitized();
        if (!data.ingredients().isEmpty()) clean.set(LogisticsDataComponents.RECIPE_CARD.get(), data);
        return clean;
    }

    public static boolean anyIn(ItemStack[] filters) {
        for (ItemStack filter : filters) {
            if (FilterItemData.hasRecipeCard(filter)) return true;
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            RecipeCardMenu.openHand(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        return RecipeCardData.of(stack).output()
                .<Component>map(output -> Component.translatable("item.logisticsnetworks.recipe_card.named",
                        output.toStack().getHoverName()))
                .orElseGet(() -> super.getName(stack));
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
