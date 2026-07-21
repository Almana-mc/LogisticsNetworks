package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.menu.PatternSetterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class PatternSetterItem extends Item {

    public PatternSetterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inv, p) -> new PatternSetterMenu(id, inv, hand),
                            Component.translatable("gui.logisticsnetworks.pattern_setter.title")),
                    buf -> buf.writeVarInt(hand.ordinal()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
