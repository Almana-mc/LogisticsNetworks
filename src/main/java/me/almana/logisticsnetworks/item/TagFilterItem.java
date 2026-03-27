package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.TagFilterData;
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

public class TagFilterItem extends Item {

    public TagFilterItem(Properties properties) {
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
                        buf.writeBoolean(true);
                        buf.writeBoolean(false);
                        buf.writeBoolean(false);
                        buf.writeBoolean(false);
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
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.deprecated.tag")
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

        tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.open_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
