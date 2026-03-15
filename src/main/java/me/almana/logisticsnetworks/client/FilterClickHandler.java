package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.OpenFilterInSlotPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.world.entity.player.Inventory;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public class FilterClickHandler {

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1)
            return;

        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen))
            return;

        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null || !hoveredSlot.hasItem())
            return;

        ItemStack stack = hoveredSlot.getItem();
        if (!stack.is(ModTags.FILTERS))
            return;

        if (!isPlayerInventorySlot(screen, hoveredSlot))
            return;

        int playerSlotIndex = hoveredSlot.getSlotIndex();
        if (playerSlotIndex < 0)
            return;

        NetworkHandler.sendToServer(new OpenFilterInSlotPayload(playerSlotIndex));
        event.setCanceled(true);
    }

    private static boolean isPlayerInventorySlot(AbstractContainerScreen<?> screen, Slot slot) {
        return slot.container instanceof Inventory;
    }
}
