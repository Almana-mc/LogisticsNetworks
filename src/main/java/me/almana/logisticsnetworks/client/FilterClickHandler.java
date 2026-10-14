package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.OpenFilterInSlotPayload;
import me.almana.logisticsnetworks.network.OpenNodeFilterPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public class FilterClickHandler {

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (ClientControls.resolveMouseAction(event.getButton()) != 1)
            return;

        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen))
            return;

        if (openHoveredFilter(screen))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (ClientControls.resolveKeyAction(event.getKeyCode(), event.getScanCode()) != 1)
            return;

        if (event.getScreen() instanceof FilterScreen)
            return;

        if (event.getScreen().getFocused() instanceof EditBox editBox && editBox.isActive())
            return;

        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen))
            return;

        if (openHoveredFilter(screen))
            event.setCanceled(true);
    }

    public static boolean openHoveredFilter(AbstractContainerScreen<?> screen) {
        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null || !hoveredSlot.hasItem())
            return false;

        ItemStack stack = hoveredSlot.getItem();
        if (!stack.is(ModTags.FILTERS))
            return false;

        if (isPlayerInventorySlot(screen, hoveredSlot)) {
            int playerSlotIndex = hoveredSlot.getSlotIndex();
            if (playerSlotIndex < 0)
                return false;
            NetworkHandler.sendToServer(new OpenFilterInSlotPayload(playerSlotIndex));
            return true;
        }

        if (screen.getMenu() instanceof NodeMenu nodeMenu) {
            int slotIndex = hoveredSlot.getContainerSlot();
            if (slotIndex < 0 || slotIndex >= 9)
                return false;
            NetworkHandler.sendToServer(new OpenNodeFilterPayload(
                    nodeMenu.getNode().getId(),
                    nodeMenu.getSelectedChannel(),
                    slotIndex));
            return true;
        }
        return false;
    }

    private static boolean isPlayerInventorySlot(AbstractContainerScreen<?> screen, Slot slot) {
        return slot.container instanceof Inventory;
    }
}
