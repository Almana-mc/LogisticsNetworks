package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.event.ScreenEvent;
import me.almana.logisticsnetworks.registration.Registration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FilterClickHandlerTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        boolean runningInIde = SharedConstants.IS_RUNNING_IN_IDE;
        SharedConstants.IS_RUNNING_IN_IDE = false;
        try {
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                    .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                    .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        } finally {
            SharedConstants.IS_RUNNING_IN_IDE = runningInIde;
        }
        var bind = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
        bind.setAccessible(true);
        bind.invoke(Registration.SMALL_FILTER.get().builtInRegistryHolder(), List.of(ModTags.FILTERS));
    }

    @AfterEach
    void restoreBinding() {
        ClientControls.SECONDARY_INTERACTION.setKey(ClientControls.SECONDARY_INTERACTION.getDefaultKey());
        KeyMapping.resetMapping();
    }

    @Test
    void nodeScreenKeepsKeyboardEventForEditorPrecedence() {
        ClientControls.SECONDARY_INTERACTION.setKey(
                InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_R));
        Inventory inventory = new Inventory(mock(Player.class), mock(EntityEquipment.class));
        inventory.setItem(0, new ItemStack(Registration.SMALL_FILTER.get()));
        Slot filterSlot = new Slot(inventory, 0, 0, 0);
        NodeScreen screen = mock(NodeScreen.class);
        when(screen.getSlotUnderMouse()).thenReturn(filterSlot);
        ScreenEvent.KeyPressed.Pre event = new ScreenEvent.KeyPressed.Pre(
                screen, new KeyEvent(InputConstants.KEY_R, 0, 0));

        try (MockedStatic<ClientPacketDistributor> packets = mockStatic(ClientPacketDistributor.class)) {
            FilterClickHandler.onKeyPressed(event);
            packets.verifyNoInteractions();
        }

        assertFalse(event.isCanceled());
    }

    @Test
    void deferredNodeRouteStillOpensPlayerInventoryFilter() {
        Inventory inventory = new Inventory(mock(Player.class), mock(EntityEquipment.class));
        inventory.setItem(0, new ItemStack(Registration.SMALL_FILTER.get()));
        NodeScreen screen = mock(NodeScreen.class);
        when(screen.getSlotUnderMouse()).thenReturn(new Slot(inventory, 0, 0, 0));

        try (MockedStatic<ClientPacketDistributor> packets = mockStatic(ClientPacketDistributor.class)) {
            assertTrue(FilterClickHandler.openHoveredFilter(screen));
            packets.verify(() -> ClientPacketDistributor.sendToServer(
                    new me.almana.logisticsnetworks.network.OpenFilterInSlotPayload(0)));
        }
    }
}
