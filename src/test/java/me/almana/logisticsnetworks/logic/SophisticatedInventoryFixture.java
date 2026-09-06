package me.almana.logisticsnetworks.logic;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.ContainerContents;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.settings.SettingsHandler;
import net.p3pp3rf1y.sophisticatedcore.settings.memory.MemorySettingsCategory;
import net.p3pp3rf1y.sophisticatedcore.settings.memory.MemorySettingsCategoryData;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.stack.StackUpgradeConfig;

import static org.mockito.Mockito.*;

final class SophisticatedInventoryFixture {
    static void loadConfig() {
        var data = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        var spec = net.p3pp3rf1y.sophisticatedstorage.Config.SERVER_SPEC;
        spec.correct(data);
        var loaded = (net.neoforged.fml.config.IConfigSpec.ILoadedConfig) mock(
                net.neoforged.fml.config.IConfigSpec.ILoadedConfig.class.getPermittedSubclasses()[0]);
        when(loaded.config()).thenReturn(data);
        spec.acceptConfig(loaded);
    }

    final ContainerContents contents = new ContainerContents();
    final IStorageWrapper wrapper = mock(IStorageWrapper.class);
    final UpgradeHandler upgrades = mock(UpgradeHandler.class);
    final MemorySettingsCategory memory;
    final Inventory inventory;
    int saves;
    int changes;

    SophisticatedInventoryFixture(int slots, int capacity) {
        memory = new MemorySettingsCategory(() -> wrapper.getInventoryHandler(),
                new MemorySettingsCategoryData(), () -> {});
        var settings = mock(SettingsHandler.class);
        when(settings.getTypeCategory(MemorySettingsCategory.class)).thenReturn(memory);
        when(wrapper.getSettingsHandler()).thenReturn(settings);
        when(wrapper.getUpgradeHandler()).thenReturn(upgrades);
        var stackConfig = net.p3pp3rf1y.sophisticatedstorage.Config.SERVER.stackUpgrade;
        inventory = new Inventory(slots, capacity, stackConfig);
        when(wrapper.getInventoryHandler()).thenReturn(inventory);
        inventory.addListener(slot -> changes++);
    }

    final class Inventory extends InventoryHandler {
        int bulkCalls;
        int slotCalls;

        Inventory(int slots, int capacity, StackUpgradeConfig config) {
            super(slots, wrapper, contents, () -> saves++, capacity, config);
        }

        @Override
        protected boolean isAllowed(ItemResource resource) {
            return true;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            bulkCalls++;
            return super.insert(resource, amount, transaction);
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext transaction) {
            slotCalls++;
            return super.insert(slot, resource, amount, transaction);
        }
    }
}
