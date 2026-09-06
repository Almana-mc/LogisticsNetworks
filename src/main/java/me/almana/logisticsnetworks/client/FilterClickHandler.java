package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.network.OpenFilterInSlotPayload;
import me.almana.logisticsnetworks.network.OpenNodeFilterPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public class FilterClickHandler {

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (ClientControls.resolveMouseAction(0, 0, event.getButton()) != 1)
            return;

        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen))
            return;

        if (openHoveredFilter(screen))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (ClientControls.resolveKeyAction(event.getKeyCode(), event.getScanCode(), event.getModifiers()) != 1)
            return;

        if (event.getScreen() instanceof FilterScreen)
            return;

        if (event.getScreen() instanceof NodeScreen)
            return;

        if (!shouldDispatchKeyboardAction(event.getScreen().getFocused()))
            return;

        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen))
            return;

        if (openHoveredFilter(screen))
            event.setCanceled(true);
    }

    static boolean shouldDispatchKeyboardAction(GuiEventListener focused) {
        return !(focused instanceof EditBox editBox && editBox.isActive());
    }

    public static boolean openHoveredFilter(AbstractContainerScreen<?> screen) {
        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null || !hoveredSlot.hasItem())
            return false;

        ItemStack stack = hoveredSlot.getItem();
        if (!stack.is(ModTags.FILTERS))
            return false;

        if (screen instanceof NodeScreen nodeScreen && screen.getMenu() instanceof NodeMenu nodeMenu
                && !isPlayerInventorySlot(screen, hoveredSlot)) {
            int filterSlot = hoveredSlot.getSlotIndex();
            if (filterSlot >= 0 && filterSlot < 9) {
                int entityId = nodeMenu.getNode().getId();
                int channel = nodeScreen.getSelectedChannel();
                ClientPacketDistributor.sendToServer(new OpenNodeFilterPayload(entityId, channel, filterSlot));
                return true;
            }
        }

        if (!isPlayerInventorySlot(screen, hoveredSlot))
            return false;

        int playerSlotIndex = hoveredSlot.getSlotIndex();
        if (playerSlotIndex < 0)
            return false;

        ClientPacketDistributor.sendToServer(new OpenFilterInSlotPayload(playerSlotIndex));
        return true;
    }

    private static boolean isPlayerInventorySlot(AbstractContainerScreen<?> screen, Slot slot) {
        return slot.container instanceof Inventory;
    }
}
