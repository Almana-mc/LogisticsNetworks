package me.almana.logisticsnetworks.utils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Collection;

@NotNullByDefault
public class ItemStackUtils {
    /**
     * Adds an {@link ItemStack} to a {@link Player}'s inventory, dropping the remainder.
     * <br/>
     * Lifted from {@link GiveCommand#giveItem(CommandSourceStack, ItemInput, Collection, int)}
     *
     * @param player Player to give the {@code stack} to
     * @param stack Stack to give the {@code player}
     */
    public static void give(Player player, ItemStack stack) {
        var maxStackSize = stack.getMaxStackSize();

        while (stack.getCount() > 0) {
            int size = Math.min(maxStackSize, stack.getCount());
            var partial = stack.split(size);

            if (player.addItem(partial) && partial.isEmpty()) {
                // No clue what this does, but GiveCommand and ServerCommandUtil both do it
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
