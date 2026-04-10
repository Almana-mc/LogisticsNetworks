package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.SlotFilterData;
import me.almana.logisticsnetworks.menu.FilterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class SlotFilterItem extends Item {

    public SlotFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, ignored) -> new FilterMenu(containerId, playerInventory, hand),
                    stack.getHoverName()), buf -> {
                        FilterMenu.writeMenuData(buf, hand, 0, false, true, false);
                    });
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        boolean blacklist = SlotFilterData.isBlacklist(stack);
        String slots = SlotFilterData.getSlotExpression(stack);
        if (slots.isBlank()) {
            slots = Component.translatable("tooltip.logisticsnetworks.filter.slot.none").getString();
        }

        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.filter.slot.desc")
                .withStyle(ChatFormatting.GRAY));

        tooltip.accept(Component.translatable(
                blacklist ? "tooltip.logisticsnetworks.filter.mode.blacklist"
                        : "tooltip.logisticsnetworks.filter.mode.whitelist")
                .withStyle(ChatFormatting.GRAY));

        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.filter.slot.value", slots)
                .withStyle(ChatFormatting.DARK_GRAY));

        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.filter.open_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.filter.deprecated.line1")
                .withStyle(ChatFormatting.RED));
        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.filter.deprecated.line2")
                .withStyle(ChatFormatting.RED));
    }
}
