package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.menu.FilterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
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

public class BaseFilterItem extends Item {

    private final int slotCount;

    public BaseFilterItem(Properties properties, int slotCount) {
        super(properties);
        this.slotCount = slotCount;
    }

    public int getSlotCount() {
        return slotCount;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inv, p) -> new FilterMenu(id, inv, hand),
                            stack.getHoverName()),
                    buf -> writeMenuData(buf, hand));
        }

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    private void writeMenuData(FriendlyByteBuf buf, InteractionHand hand) {
        FilterMenu.writeMenuData(buf, hand, slotCount, false, false, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        boolean blacklist = FilterItemData.isBlacklist(stack);
        int entryCount = FilterItemData.getEntryCount(stack);

        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.filter.base.desc", slotCount)
                .withStyle(ChatFormatting.GRAY));

        String modeKey = blacklist ? "tooltip.logisticsnetworks.filter.mode.blacklist"
                : "tooltip.logisticsnetworks.filter.mode.whitelist";
        tooltip.accept(Component.translatable(modeKey).withStyle(ChatFormatting.GRAY));

        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.filter.entries", entryCount, slotCount)
                .withStyle(ChatFormatting.DARK_GRAY));

        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.filter.open_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
