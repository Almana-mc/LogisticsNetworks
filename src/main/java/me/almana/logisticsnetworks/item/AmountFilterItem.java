package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.AmountFilterData;
import me.almana.logisticsnetworks.menu.FilterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
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
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, ignored) -> new FilterMenu(containerId, playerInventory, hand),
                    stack.getHoverName()), buf -> {
                        buf.writeVarInt(hand.ordinal());
                        buf.writeVarInt(0);
                        buf.writeBoolean(false);
                        buf.writeBoolean(true);
                        buf.writeBoolean(false);
                        buf.writeBoolean(false);
                        buf.writeBoolean(false);
                    });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.amount.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.logisticsnetworks.filter.amount",
                AmountFilterData.getAmount(stack)).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.open_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
