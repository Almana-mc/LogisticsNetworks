package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.OpenFilterInSlotPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
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

        // Only intercept clicks on player inventory slots, not container slots
        if (!isPlayerInventorySlot(screen, hoveredSlot))
            return;

        int playerSlotIndex = hoveredSlot.getSlotIndex();
        if (playerSlotIndex < 0)
            return;

        ClientPacketDistributor.sendToServer(new OpenFilterInSlotPayload(playerSlotIndex));
        event.setCanceled(true);
    }

    private static boolean isPlayerInventorySlot(AbstractContainerScreen<?> screen, Slot slot) {
        return slot.container instanceof Inventory;
    }
}
