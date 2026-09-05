package me.almana.logisticsnetworks.logic;

import io.netty.buffer.Unpooled;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.network.ScanAttachedStoragePayload;
import me.almana.logisticsnetworks.network.SyncFilterScanResultPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachedStorageFilterScannerTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        ComponentCodecTest.bootstrap();
    }

    @Test
    void itemScanKeepsFirstComponentVariantAndSkipsDuplicates() {
        ItemStack firstIron = new ItemStack(Items.IRON_INGOT, 4);
        firstIron.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
        ItemStack secondIron = new ItemStack(Items.IRON_INGOT, 7);
        secondIron.set(DataComponents.CUSTOM_NAME, Component.literal("second"));
        var storage = items(ItemStack.EMPTY, firstIron, secondIron, new ItemStack(Items.DIAMOND, 2));
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());

        var result = AttachedStorageFilterScanner.scanItems(storage, filter, ComponentMigrationTest.registries());

        assertEquals(new AttachedStorageFilterScanner.Result(2, true, false), result);
        assertTrue(ItemStack.isSameItemSameComponents(firstIron.copyWithCount(1),
                FilterItemData.getEntry(filter, 0, ComponentMigrationTest.registries())));
        assertTrue(FilterItemData.getEntry(filter, 1, ComponentMigrationTest.registries()).is(Items.DIAMOND));
        assertTrue(filter.has(LogisticsDataComponents.FILTER_ENTRIES));
        assertFalse(filter.has(DataComponents.CUSTOM_DATA));
    }

    @Test
    void fullStatusRequiresAnUnseenEntry() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        for (int slot = 0; slot < FilterItemData.getCapacity(filter); slot++) {
            FilterItemData.setEntry(filter, slot, new ItemStack(Items.IRON_INGOT),
                    ComponentMigrationTest.registries());
        }

        var duplicates = AttachedStorageFilterScanner.scanItems(items(new ItemStack(Items.IRON_INGOT)), filter,
                ComponentMigrationTest.registries());
        var unseen = AttachedStorageFilterScanner.scanItems(items(new ItemStack(Items.GOLD_INGOT)), filter,
                ComponentMigrationTest.registries());

        assertEquals(new AttachedStorageFilterScanner.Result(0, true, false), duplicates);
        assertEquals(new AttachedStorageFilterScanner.Result(0, true, true), unseen);
    }

    @Test
    void emptyAndMissingItemStorageKeepSourceStatuses() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());

        assertEquals(new AttachedStorageFilterScanner.Result(0, true, false),
                AttachedStorageFilterScanner.scanItems(items(), filter, ComponentMigrationTest.registries()));
        assertEquals(new AttachedStorageFilterScanner.Result(0, false, false),
                AttachedStorageFilterScanner.scanItems(null, filter, ComponentMigrationTest.registries()));
    }

    @Test
    void fluidScanSkipsExactDuplicatesAndAddsDifferentFluids() {
        var storage = fluids(FluidStack.EMPTY, new FluidStack(Fluids.WATER, 250),
                new FluidStack(Fluids.WATER, 1000), new FluidStack(Fluids.LAVA, 500));
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        FilterItemData.setTargetType(filter, FilterTargetType.FLUIDS);

        var result = AttachedStorageFilterScanner.scanFluids(storage, filter);

        assertEquals(new AttachedStorageFilterScanner.Result(2, true, false), result);
        assertTrue(FilterItemData.getFluidEntry(filter, 0).is(Fluids.WATER));
        assertTrue(FilterItemData.getFluidEntry(filter, 1).is(Fluids.LAVA));
    }

    @Test
    void selectedSideDrivesLiveCapabilityLookup() {
        ServerLevel level = mock(ServerLevel.class);
        LogisticsNodeEntity node = mock(LogisticsNodeEntity.class);
        TransferCapabilityCache capabilities = mock(TransferCapabilityCache.class);
        ChannelData channel = new ChannelData(true);
        channel.setType(ChannelType.ITEM);
        channel.setIoDirection(Direction.SOUTH);
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        var storage = items(new ItemStack(Items.IRON_INGOT));
        when(level.isLoaded(BlockPos.ZERO)).thenReturn(true);
        when(level.registryAccess()).thenReturn(ComponentMigrationTest.registries());
        when(node.getAttachedPos()).thenReturn(BlockPos.ZERO);
        when(node.capabilities()).thenReturn(capabilities);
        when(capabilities.findItemHandler(Direction.SOUTH)).thenReturn(storage);

        var result = AttachedStorageFilterScanner.scan(level, node, channel, filter);

        assertEquals(new AttachedStorageFilterScanner.Result(1, true, false), result);
        verify(capabilities).findItemHandler(Direction.SOUTH);
        verify(capabilities, never()).findFluidHandler(Direction.SOUTH);
    }

    @Test
    void unloadedAndUnsupportedTargetsReportNoStorage() {
        ServerLevel level = mock(ServerLevel.class);
        LogisticsNodeEntity node = mock(LogisticsNodeEntity.class);
        TransferCapabilityCache capabilities = mock(TransferCapabilityCache.class);
        ChannelData channel = new ChannelData(true);
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        when(node.getAttachedPos()).thenReturn(BlockPos.ZERO);
        when(node.capabilities()).thenReturn(capabilities);

        assertEquals(new AttachedStorageFilterScanner.Result(0, false, false),
                AttachedStorageFilterScanner.scan(level, node, channel, filter));
        verify(capabilities, never()).findItemHandler(Direction.UP);

        when(level.isLoaded(BlockPos.ZERO)).thenReturn(true);
        channel.setType(ChannelType.ENERGY);
        assertEquals(new AttachedStorageFilterScanner.Result(0, false, false),
                AttachedStorageFilterScanner.scan(level, node, channel, filter));
        channel.setType(ChannelType.CHEMICAL);
        assertEquals(new AttachedStorageFilterScanner.Result(0, false, false),
                AttachedStorageFilterScanner.scan(level, node, channel, filter));
    }

    @Test
    void scanPayloadsRoundTripFilterComponentsAndStatus() {
        ItemStack filter = new ItemStack(Registration.SMALL_FILTER.get());
        FilterItemData.addItem(filter, new ItemStack(Items.DIAMOND), ComponentMigrationTest.registries());
        var outgoing = new SyncFilterScanResultPayload(filter, 1, true, false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), ComponentMigrationTest.registries());
        try {
            ScanAttachedStoragePayload.STREAM_CODEC.encode(buffer, ScanAttachedStoragePayload.INSTANCE);
            assertEquals(ScanAttachedStoragePayload.INSTANCE,
                    ScanAttachedStoragePayload.STREAM_CODEC.decode(buffer));
            SyncFilterScanResultPayload.STREAM_CODEC.encode(buffer, outgoing);
            SyncFilterScanResultPayload incoming = SyncFilterScanResultPayload.STREAM_CODEC.decode(buffer);
            assertTrue(ItemStack.isSameItemSameComponents(outgoing.filter(), incoming.filter()));
            assertEquals(outgoing.added(), incoming.added());
            assertEquals(outgoing.storageFound(), incoming.storageFound());
            assertEquals(outgoing.filterFull(), incoming.filterFull());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static ItemStacksResourceHandler items(ItemStack... stacks) {
        return new ItemStacksResourceHandler(NonNullList.of(ItemStack.EMPTY, stacks));
    }

    private static FluidStacksResourceHandler fluids(FluidStack... stacks) {
        return new FluidStacksResourceHandler(NonNullList.of(FluidStack.EMPTY, stacks), 8_000);
    }
}
