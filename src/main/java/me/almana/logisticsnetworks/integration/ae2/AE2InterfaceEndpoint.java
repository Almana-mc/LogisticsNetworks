package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogicHost;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AE2InterfaceEndpoint implements StorageEndpoint {

    private final InterfaceLogicHost host;
    private final IActionHost actionHost;
    private final IGridNode node;
    private final IGrid grid;

    AE2InterfaceEndpoint(InterfaceLogicHost host, IActionHost actionHost, IGridNode node) {
        this.host = host;
        this.actionHost = actionHost;
        this.node = node;
        this.grid = node.getGrid();
    }

    @Override
    public Object networkIdentity() {
        return grid;
    }

    @Override
    public boolean isValid() {
        return node.isActive() && node.getGrid() == grid;
    }

    @Override
    public List<StoredItem> exportableItems() {
        return storedItems(true);
    }

    @Override
    public List<StoredItem> storedItems() {
        return storedItems(false);
    }

    private List<StoredItem> storedItems(boolean exportOnly) {
        List<StoredItem> items = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : storage().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey key && (!exportOnly || canExport(key))) {
                items.add(new StoredItem(key.toStack(), entry.getLongValue()));
            }
        }
        items.sort(Comparator.comparing((StoredItem item) ->
                        BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString())
                .thenComparing(item -> item.stack().getComponentsPatch().toString()));
        return items;
    }

    @Override
    public List<StoredFluid> exportableFluids() {
        return storedFluids(true);
    }

    @Override
    public List<StoredFluid> storedFluids() {
        return storedFluids(false);
    }

    private List<StoredFluid> storedFluids(boolean exportOnly) {
        List<StoredFluid> fluids = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : storage().getAvailableStacks()) {
            if (entry.getKey() instanceof AEFluidKey key && (!exportOnly || canExport(key))) {
                fluids.add(new StoredFluid(key.toStack(1), entry.getLongValue()));
            }
        }
        fluids.sort(Comparator.comparing((StoredFluid fluid) ->
                        BuiltInRegistries.FLUID.getKey(fluid.stack().getFluid()).toString())
                .thenComparing(fluid -> fluid.stack().getComponentsPatch().toString()));
        return fluids;
    }

    @Override
    public long insertItem(ItemStack stack, long amount, boolean simulate) {
        AEItemKey key = AEItemKey.of(stack);
        return key == null ? 0 : insert(key, amount, simulate);
    }

    @Override
    public long extractItem(ItemStack stack, long amount, boolean simulate) {
        AEItemKey key = AEItemKey.of(stack);
        return key == null ? 0 : extract(key, amount, simulate);
    }

    @Override
    public long insertFluid(FluidStack stack, long amount, boolean simulate) {
        AEFluidKey key = AEFluidKey.of(stack);
        return key == null ? 0 : insert(key, amount, simulate);
    }

    @Override
    public long extractFluid(FluidStack stack, long amount, boolean simulate) {
        AEFluidKey key = AEFluidKey.of(stack);
        return key == null ? 0 : extract(key, amount, simulate);
    }

    private long insert(AEKey key, long amount, boolean simulate) {
        if (!isValid() || amount <= 0) return 0;
        return storage().insert(key, amount, actionable(simulate), IActionSource.ofMachine(actionHost));
    }

    private long extract(AEKey key, long amount, boolean simulate) {
        if (!isValid() || amount <= 0) return 0;
        return storage().extract(key, amount, actionable(simulate), IActionSource.ofMachine(actionHost));
    }

    private boolean canExport(AEKey candidate) {
        boolean fuzzy = host.getUpgrades().isInstalled(AEItems.FUZZY_CARD);
        FuzzyMode fuzzyMode = host.getConfigManager().getSetting(Settings.FUZZY_MODE);
        for (int slot = 0; slot < host.getConfig().size(); slot++) {
            GenericStack configured = host.getConfig().getStack(slot);
            if (configured == null) continue;
            if (configured.what().equals(candidate)) return true;
            if (fuzzy && configured.what().fuzzyEquals(candidate, fuzzyMode)) return true;
        }
        return false;
    }

    private MEStorage storage() {
        return grid.getStorageService().getInventory();
    }

    private static Actionable actionable(boolean simulate) {
        return simulate ? Actionable.SIMULATE : Actionable.MODULATE;
    }
}
