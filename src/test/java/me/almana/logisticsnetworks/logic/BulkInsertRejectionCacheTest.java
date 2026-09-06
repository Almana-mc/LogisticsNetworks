package me.almana.logisticsnetworks.logic;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulkInsertRejectionCacheTest {
    @BeforeAll
    static void bootstrap() {
        TransferParityTest.bootstrap();
    }

    @Test
    void cachedDescriptorOwnsComponentsAndDistinguishesHandlersAndCounts() {
        var first = new EqualInventory();
        var second = new EqualInventory();
        assertEquals(first, second);
        var stack = new ItemStack(Items.IRON_INGOT, 4);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("original"));
        var original = ItemResource.of(stack);
        var cache = new BulkInsertRejectionCache();
        cache.reject(first, original, 4);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        assertTrue(cache.isRejected(first, original, 4));
        assertFalse(cache.isRejected(first, ItemResource.of(stack), 4));
        assertFalse(cache.isRejected(first, original, 3));
        assertFalse(cache.isRejected(second, original, 4));
        cache.clear();
        assertFalse(cache.isRejected(first, original, 4));
    }

    private static final class EqualInventory extends net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler {
        EqualInventory() {
            super(1);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualInventory;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
