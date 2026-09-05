package me.almana.logisticsnetworks.logic;

import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.filter.FilterItemData;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TransferParityTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        boolean runningInIde = SharedConstants.IS_RUNNING_IN_IDE;
        SharedConstants.IS_RUNNING_IN_IDE = false;
        try {
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                    .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        } finally {
            SharedConstants.IS_RUNNING_IN_IDE = runningInIde;
        }
    }

    @Test
    void roundRobinDividesEightItemsEqually() throws Exception {
        var source = inventory(8);
        var first = inventory(0);
        var second = inventory(0);
        assertEquals(8, move(source, List.of(first, second), true));
        assertEquals(4, first.getAmountAsInt(0));
        assertEquals(4, second.getAmountAsInt(0));
        assertEquals(0, source.getAmountAsInt(0));
    }

    @Test
    void priorityPreservesSourceSlotTraversal() throws Exception {
        var source = inventory(2, 2, 4);
        var first = inventory(0);
        var second = inventory(0);
        assertEquals(8, move(source, List.of(first, second), false));
        assertEquals(6, first.getAmountAsInt(0));
        assertEquals(2, second.getAmountAsInt(0));
    }

    @Test
    void prioritySendsEightItemStackToFirstTarget() throws Exception {
        var source = inventory(8);
        var first = inventory(0);
        var second = inventory(0);
        assertEquals(8, move(source, List.of(first, second), false));
        assertEquals(8, first.getAmountAsInt(0));
        assertEquals(0, second.getAmountAsInt(0));
    }

    @Test
    void roundRobinRoundsUpRemainingShare() throws Exception {
        var source = inventory(8);
        var first = inventory(0);
        var second = inventory(0);
        var third = inventory(0);
        assertEquals(8, move(source, List.of(first, second, third), true));
        assertEquals(3, first.getAmountAsInt(0));
        assertEquals(3, second.getAmountAsInt(0));
        assertEquals(2, third.getAmountAsInt(0));
    }

    @Test
    void roundRobinCollectsShareAcrossSourceSlots() throws Exception {
        var source = inventory(2, 6);
        var first = inventory(0);
        var second = inventory(0);
        assertEquals(8, move(source, List.of(first, second), true));
        assertEquals(4, first.getAmountAsInt(0));
        assertEquals(4, second.getAmountAsInt(0));
    }

    @Test
    void redistributesUnusedCapacityToLaterTarget() throws Exception {
        var source = inventory(8);
        var first = inventory(62);
        var second = inventory(0);
        assertEquals(8, move(source, List.of(first, second), true));
        assertEquals(64, first.getAmountAsInt(0));
        assertEquals(6, second.getAmountAsInt(0));
    }

    @Test
    void rollsBackInsertionWhenExtractionChanges() throws Exception {
        var source = new ItemStacksResourceHandler(NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT, 8))) {
            private int attempts;
            @Override
            public int extract(int slot, ItemResource resource, int amount, TransactionContext transaction) {
                return ++attempts % 2 == 0 ? 0 : super.extract(slot, resource, amount, transaction);
            }
        };
        var target = inventory(0);
        assertEquals(0, move(source, List.of(target), false));
        assertEquals(8, source.getAmountAsInt(0));
        assertEquals(0, target.getAmountAsInt(0));
    }

    static ItemStacksResourceHandler inventory(int... counts) {
        var stacks = NonNullList.withSize(counts.length, ItemStack.EMPTY);
        for (int i = 0; i < counts.length; i++)
            stacks.set(i, counts[i] == 0 ? ItemStack.EMPTY : new ItemStack(Items.IRON_INGOT, counts[i]));
        return new ItemStacksResourceHandler(stacks);
    }

    static int move(ResourceHandler<ItemResource> source, List<? extends ResourceHandler<ItemResource>> handlers,
            boolean roundRobin) throws Exception {
        return move(source, handlers, roundRobin, new ItemStack[0], new ItemStack[0]);
    }

    static int move(ResourceHandler<ItemResource> source, List<? extends ResourceHandler<ItemResource>> handlers,
            boolean roundRobin, ItemStack[] exports, ItemStack[] imports) throws Exception {
        var type = Class.forName(TransferEngine.class.getName() + "$ItemTransferTarget");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var cache = FilterItemData.createReadCache();
        var targets = new ArrayList<>();
        for (var handler : handlers)
            targets.add(constructor.newInstance(handler, imports, FilterMode.MATCH_ALL,
                    TransferAmountRules.collect(exports, imports, cache), false, null, false));
        var method = TransferEngine.class.getDeclaredMethod("executeMove", ResourceHandler.class, List.class,
                int.class, ItemStack[].class, FilterMode.class, boolean[].class,
                net.minecraft.core.HolderLookup.Provider.class, boolean.class, FilterItemData.ReadCache.class);
        method.setAccessible(true);
        return (int) method.invoke(null, source, targets, 8, exports, FilterMode.MATCH_ALL, null,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), roundRobin, cache);
    }
}
