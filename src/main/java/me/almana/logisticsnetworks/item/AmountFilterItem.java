package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.AmountFilterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class AmountFilterItem extends Item {

    public AmountFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(
                Component.translatable("tooltip.logisticsnetworks.filter.deprecated")
                        .withStyle(ChatFormatting.RED));

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.amount.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.logisticsnetworks.filter.amount",
                AmountFilterData.getAmount(stack)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
