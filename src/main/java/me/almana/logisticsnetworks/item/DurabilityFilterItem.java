package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.DurabilityFilterData;
import me.almana.logisticsnetworks.menu.FilterMenu;
import java.util.List;
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
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class DurabilityFilterItem extends Item {

    public DurabilityFilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (containerId, playerInventory, ignored) -> new FilterMenu(containerId, playerInventory, hand),
                    stack.getHoverName()), buf -> {
                        buf.writeVarInt(hand.ordinal());
                        buf.writeVarInt(0);
                        buf.writeBoolean(false);
                        buf.writeBoolean(false);
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.deprecated")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.deprecated.durability")
                .withStyle(ChatFormatting.RED));

        DurabilityFilterData.Operator operator = DurabilityFilterData.getOperator(stack);
        int value = DurabilityFilterData.getValue(stack);

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.durability.desc")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(
                "tooltip.logisticsnetworks.filter.durability",
                operator.symbol(),
                value).withStyle(ChatFormatting.DARK_GRAY));

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.open_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
