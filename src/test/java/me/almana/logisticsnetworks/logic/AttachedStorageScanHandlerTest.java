package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.network.ScanAttachedStoragePayload;
import me.almana.logisticsnetworks.network.ServerPayloadHandler;
import me.almana.logisticsnetworks.network.SyncFilterScanResultPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachedStorageScanHandlerTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        ComponentCodecTest.bootstrap();
    }

    @Test
    void wrongMenuNeverReachesTheScanner() {
        IPayloadContext context = mock(IPayloadContext.class);
        ServerPlayer player = mock(ServerPlayer.class);
        player.containerMenu = mock(AbstractContainerMenu.class);
        when(context.player()).thenReturn(player);

        try (var scanner = mockStatic(AttachedStorageFilterScanner.class)) {
            runRequest(context);
            scanner.verifyNoInteractions();
        }
    }

    @Test
    void deniedNodeAccessNeverReachesTheScanner() {
        IPayloadContext context = mock(IPayloadContext.class);
        ServerPlayer player = mock(ServerPlayer.class);
        FilterMenu menu = mock(FilterMenu.class);
        LogisticsNodeEntity node = mock(LogisticsNodeEntity.class);
        player.containerMenu = menu;
        when(context.player()).thenReturn(player);
        when(menu.canScanAttachedStorage()).thenReturn(true);
        when(menu.getNodeSource()).thenReturn(node);
        when(node.isAlive()).thenReturn(true);
        when(node.isOwnedBy(player)).thenReturn(false);

        try (var scanner = mockStatic(AttachedStorageFilterScanner.class)) {
            runRequest(context);
            scanner.verifyNoInteractions();
            verify(node).isOwnedBy(player);
        }
    }

    @Test
    void removedNodeNeverReachesTheScanner() {
        IPayloadContext context = mock(IPayloadContext.class);
        ServerPlayer player = mock(ServerPlayer.class);
        FilterMenu menu = mock(FilterMenu.class);
        LogisticsNodeEntity node = mock(LogisticsNodeEntity.class);
        player.containerMenu = menu;
        when(context.player()).thenReturn(player);
        when(menu.canScanAttachedStorage()).thenReturn(true);
        when(menu.getNodeSource()).thenReturn(node);
        when(node.isAlive()).thenReturn(false);

        try (var scanner = mockStatic(AttachedStorageFilterScanner.class)) {
            runRequest(context);
            scanner.verifyNoInteractions();
            verify(node, never()).isOwnedBy(player);
        }
    }

    @Test
    void replacedGeneralFilterNeverReachesTheScanner() {
        IPayloadContext context = mock(IPayloadContext.class);
        ServerPlayer player = mock(ServerPlayer.class);
        FilterMenu menu = mock(FilterMenu.class);
        LogisticsNodeEntity node = mock(LogisticsNodeEntity.class);
        ChannelData channel = new ChannelData(true);
        player.containerMenu = menu;
        when(context.player()).thenReturn(player);
        when(menu.canScanAttachedStorage()).thenReturn(true);
        when(menu.getNodeSource()).thenReturn(node);
        when(menu.getNodeChannel()).thenReturn(0);
        when(node.isAlive()).thenReturn(true);
        when(node.isOwnedBy(player)).thenReturn(true);
        when(node.getChannel(0)).thenReturn(channel);
        channel.setFilterItem(0, new ItemStack(Items.PAPER));
        when(menu.getNodeFilterSlot()).thenReturn(0);
        when(menu.getOpenedStack()).thenReturn(channel.getFilterItem(0));

        try (var scanner = mockStatic(AttachedStorageFilterScanner.class)) {
            runRequest(context);
            scanner.verifyNoInteractions();
        }
    }

    @Test
    void successfulScanRefreshesMenuAndInvalidatesNetworkCache() {
        IPayloadContext context = mock(IPayloadContext.class);
        ServerPlayer player = mock(ServerPlayer.class);
        FilterMenu menu = mock(FilterMenu.class);
        LogisticsNodeEntity node = mock(LogisticsNodeEntity.class);
        ServerLevel level = mock(ServerLevel.class);
        ChannelData channel = new ChannelData(true);
        UUID networkId = UUID.randomUUID();
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        player.containerMenu = menu;
        when(context.player()).thenReturn(player);
        when(menu.canScanAttachedStorage()).thenReturn(true);
        when(menu.getNodeSource()).thenReturn(node);
        when(menu.getNodeChannel()).thenReturn(0);
        when(menu.getNodeFilterSlot()).thenReturn(0);
        when(menu.getOpenedStack()).thenReturn(filter);
        when(node.isAlive()).thenReturn(true);
        when(node.isOwnedBy(player)).thenReturn(true);
        when(node.getChannel(0)).thenReturn(channel);
        when(node.level()).thenReturn(level);
        when(node.getNetworkId()).thenReturn(networkId);
        var registry = mock(me.almana.logisticsnetworks.data.NetworkRegistry.class);

        try (var scanner = mockStatic(AttachedStorageFilterScanner.class);
                var registries = mockStatic(me.almana.logisticsnetworks.data.NetworkRegistry.class);
                var packets = mockStatic(PacketDistributor.class)) {
            scanner.when(() -> AttachedStorageFilterScanner.scan(level, node, channel, filter))
                    .thenReturn(new AttachedStorageFilterScanner.Result(2, true, false));
            registries.when(() -> me.almana.logisticsnetworks.data.NetworkRegistry.get(level)).thenReturn(registry);

            runRequest(context);

            verify(menu).refreshFilterEntries();
            verify(registry).markNetworkDirty(networkId);
            packets.verify(() -> PacketDistributor.sendToPlayer(same(player), any(SyncFilterScanResultPayload.class),
                    any(CustomPacketPayload[].class)));
        }
    }

    private static void runRequest(IPayloadContext context) {
        ServerPayloadHandler.handleScanAttachedStorage(ScanAttachedStoragePayload.INSTANCE, context);
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);
        verify(context).enqueueWork(work.capture());
        work.getValue().run();
    }
}
