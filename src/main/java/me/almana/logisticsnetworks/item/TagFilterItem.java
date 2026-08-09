package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.TagFilterData;
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

public class TagFilterItem extends Item {

    public TagFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.deprecated")
                .withStyle(ChatFormatting.RED));

        boolean blacklist = TagFilterData.isBlacklist(stack);
        int tagCount = TagFilterData.getTagFilterCount(stack);

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.tag.desc")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(
                blacklist ? "tooltip.logisticsnetworks.filter.mode.blacklist"
                        : "tooltip.logisticsnetworks.filter.mode.whitelist")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(
                "tooltip.logisticsnetworks.filter.tags",
                tagCount).withStyle(ChatFormatting.DARK_GRAY));
    }
}
