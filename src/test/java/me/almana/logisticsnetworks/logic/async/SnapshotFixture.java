package me.almana.logisticsnetworks.logic.async;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

class SnapshotFixture {
    static ItemResource IRON;
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        boolean ide = SharedConstants.IS_RUNNING_IN_IDE;
        SharedConstants.IS_RUNNING_IN_IDE = false;
        try {
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                    .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        } finally {
            SharedConstants.IS_RUNNING_IN_IDE = ide;
        }
        IRON = ItemResource.of(Items.IRON_INGOT);
    }
    @BeforeEach
    void mark() { ThreadGuard.markServerThread(); }
    @AfterEach
    void clear() { ThreadGuard.clearServerThread(); }
    static ItemStacksResourceHandler inventory(int... counts) {
        var stacks = NonNullList.withSize(counts.length, ItemStack.EMPTY);
        for (int i = 0; i < counts.length; i++)
            stacks.set(i, counts[i] == 0 ? ItemStack.EMPTY : new ItemStack(Items.IRON_INGOT, counts[i]));
        return new ItemStacksResourceHandler(stacks);
    }
}
