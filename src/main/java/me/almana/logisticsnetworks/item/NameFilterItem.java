package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.NameFilterData;
import me.almana.logisticsnetworks.filter.NameMatchScope;
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

public class NameFilterItem extends Item {

    public NameFilterItem(Properties properties) {
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
                        buf.writeBoolean(false); // tag
                        buf.writeBoolean(false); // amount
                        buf.writeBoolean(false); // nbt
                        buf.writeBoolean(false); // durability
                        buf.writeBoolean(false); // mod
                        buf.writeBoolean(false); // slot
                        buf.writeBoolean(true);  // name
                    });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean blacklist = NameFilterData.isBlacklist(stack);
        String name = NameFilterData.getNameFilter(stack);
        String selected = name.isEmpty()
                ? Component.translatable("tooltip.logisticsnetworks.filter.name.none").getString()
                : name;

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.name.desc")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(
                blacklist ? "tooltip.logisticsnetworks.filter.mode.blacklist"
                        : "tooltip.logisticsnetworks.filter.mode.whitelist")
                .withStyle(ChatFormatting.GRAY));

        NameMatchScope scope = NameFilterData.getMatchScope(stack);
        String scopeKey = switch (scope) {
            case TOOLTIP -> "gui.logisticsnetworks.filter.name.scope.tooltip";
            case BOTH -> "gui.logisticsnetworks.filter.name.scope.both";
            default -> "gui.logisticsnetworks.filter.name.scope.name";
        };
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.name.scope",
                Component.translatable(scopeKey)).withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable(
                "tooltip.logisticsnetworks.filter.name",
                selected).withStyle(ChatFormatting.DARK_GRAY));

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.open_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
