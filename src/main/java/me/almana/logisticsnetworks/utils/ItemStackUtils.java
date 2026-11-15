package me.almana.logisticsnetworks.utils;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ItemStackUtils {
    // Mirrors GiveCommand.giveItem
    public static void give(Player player, ItemStack stack) {
        var maxStackSize = stack.getMaxStackSize();

        while (stack.getCount() > 0) {
            int size = Math.min(maxStackSize, stack.getCount());
            var partial = stack.split(size);

            if (player.addItem(partial) && partial.isEmpty()) {
                var itemEntity = player.drop(partial, false);
                if (itemEntity != null) {
                    itemEntity.makeFakeItem();
                }

                player.level()
                        .playSound(
                                null,
                                player.getX(),
                                player.getY(),
                                player.getZ(),
                                SoundEvents.ITEM_PICKUP,
                                SoundSource.PLAYERS,
                                0.2F,
                                ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
                        );
                player.containerMenu.broadcastChanges();
            } else {
                var itemEntity = player.drop(partial, false);
                if (itemEntity != null) {
                    itemEntity.setNoPickUpDelay();
                    itemEntity.setTarget(player.getUUID());
                }
            }
        }
    }
}
