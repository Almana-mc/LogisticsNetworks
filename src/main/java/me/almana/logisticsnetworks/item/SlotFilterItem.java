package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.SlotFilterData;
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

public class SlotFilterItem extends Item {

    public SlotFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean blacklist = SlotFilterData.isBlacklist(stack);
        String slots = SlotFilterData.getSlotExpression(stack);
        if (slots.isBlank()) {
            slots = Component.translatable("tooltip.logisticsnetworks.filter.slot.none").getString();
        }

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.slot.desc")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(
                blacklist ? "tooltip.logisticsnetworks.filter.mode.blacklist"
                        : "tooltip.logisticsnetworks.filter.mode.whitelist")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.slot.value", slots)
                .withStyle(ChatFormatting.DARK_GRAY));

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.deprecated.line1")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.deprecated.line2")
                .withStyle(ChatFormatting.RED));
    }
}
