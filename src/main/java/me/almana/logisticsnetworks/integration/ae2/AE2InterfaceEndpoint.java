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
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogicHost;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AE2InterfaceEndpoint implements StorageEndpoint {

    private record AvailableKey(AEKey key, long amount) {
    }

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
    public Object endpointIdentity() {
        return host;
    }

    @Override
    public boolean isValid() {
        return node.isActive() && node.getGrid() == grid && !host.getBlockEntity().isRemoved();
    }

    @Override
    public List<StoredItem> exportableItems(boolean fresh) {
        if (!isValid()) return List.of();
        KeyCounter available = available(fresh);
        List<StoredItem> items = new ArrayList<>();
        Set<AEKey> seen = new HashSet<>();
        var config = host.getConfig();
        boolean fuzzy = fuzzy();
        FuzzyMode mode = fuzzy ? fuzzyMode() : FuzzyMode.IGNORE_ALL;
        Set<Object> configuredItems = Collections.newSetFromMap(new IdentityHashMap<>());
        if (fuzzy) {
            for (int slot = 0; slot < config.size(); slot++) {
                GenericStack configured = config.getStack(slot);
                if (configured != null && configured.what() instanceof AEItemKey key) {
                    configuredItems.add(key.getPrimaryKey());
                }
            }
        }
        Map<Object, List<AvailableKey>> variants = fuzzy ? variants(available, configuredItems) : Map.of();
        for (int slot = 0; slot < config.size(); slot++) {
            GenericStack configured = config.getStack(slot);
            if (configured == null || !(configured.what() instanceof AEItemKey key)) continue;
            addItems(items, seen, available, variants, key, fuzzy, mode);
        }
        return items;
    }

    @Override
    public Map<Item, Long> itemCounts(Set<Item> items, boolean fresh) {
        if (items.isEmpty() || !isValid()) return Map.of();
        KeyCounter available = available(fresh);
        Map<Item, Long> counts = new IdentityHashMap<>();
        for (Item item : items) counts.put(item, 0L);
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (entry.getKey() instanceof AEItemKey key
                    && entry.getLongValue() > 0 && items.contains(key.getItem())) {
                counts.merge(key.getItem(), entry.getLongValue(), AE2InterfaceEndpoint::saturatingAdd);
            }
        }
        return counts;
    }

    @Override
    public List<StoredFluid> exportableFluids(boolean fresh) {
        if (!isValid()) return List.of();
        KeyCounter available = available(fresh);
        List<StoredFluid> fluids = new ArrayList<>();
        Set<AEKey> seen = new HashSet<>();
        var config = host.getConfig();
        boolean fuzzy = fuzzy();
        FuzzyMode mode = fuzzy ? fuzzyMode() : FuzzyMode.IGNORE_ALL;
        Set<Object> configuredFluids = Collections.newSetFromMap(new IdentityHashMap<>());
        if (fuzzy) {
            for (int slot = 0; slot < config.size(); slot++) {
                GenericStack configured = config.getStack(slot);
                if (configured != null && configured.what() instanceof AEFluidKey key) {
                    configuredFluids.add(key.getPrimaryKey());
                }
            }
        }
        Map<Object, List<AvailableKey>> variants = fuzzy ? variants(available, configuredFluids) : Map.of();
        for (int slot = 0; slot < config.size(); slot++) {
            GenericStack configured = config.getStack(slot);
            if (configured == null || !(configured.what() instanceof AEFluidKey key)) continue;
            addFluids(fluids, seen, available, variants, key, fuzzy, mode);
        }
        return fluids;
    }

    @Override
    public long fluidCount(FluidStack stack, boolean fresh) {
        if (stack.isEmpty() || !isValid()) return 0;
        AEFluidKey key = AEFluidKey.of(stack);
        return key == null ? 0 : Math.max(0, available(fresh).get(key));
    }

    @Override
    public boolean canExportItem(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        return key != null && isValid() && canExport(key);
    }

    @Override
    public boolean canExportFluid(FluidStack stack) {
        AEFluidKey key = AEFluidKey.of(stack);
        return key != null && isValid() && canExport(key);
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

    private void addItems(List<StoredItem> items, Set<AEKey> seen, KeyCounter available,
                          Map<Object, List<AvailableKey>> variants, AEItemKey configured,
                          boolean fuzzy, FuzzyMode mode) {
        if (!fuzzy) {
            long amount = available.get(configured);
            if (amount > 0 && seen.add(configured)) items.add(new StoredItem(configured.toStack(), amount));
            return;
        }
        for (AvailableKey entry : variants.getOrDefault(configured.getPrimaryKey(), List.of())) {
            if (entry.key() instanceof AEItemKey key
                    && configured.fuzzyEquals(key, mode) && seen.add(key)) {
                items.add(new StoredItem(key.toStack(), entry.amount()));
            }
        }
    }

    private void addFluids(List<StoredFluid> fluids, Set<AEKey> seen, KeyCounter available,
                           Map<Object, List<AvailableKey>> variants, AEFluidKey configured,
                           boolean fuzzy, FuzzyMode mode) {
        if (!fuzzy) {
            long amount = available.get(configured);
            if (amount > 0 && seen.add(configured)) fluids.add(new StoredFluid(configured.toStack(1), amount));
            return;
        }
        for (AvailableKey entry : variants.getOrDefault(configured.getPrimaryKey(), List.of())) {
            if (entry.key() instanceof AEFluidKey key
                    && configured.fuzzyEquals(key, mode) && seen.add(key)) {
                fluids.add(new StoredFluid(key.toStack(1), entry.amount()));
            }
        }
    }

    private Map<Object, List<AvailableKey>> variants(KeyCounter available, Set<Object> configured) {
        Map<Object, List<AvailableKey>> variants = new IdentityHashMap<>();
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (entry.getLongValue() <= 0) continue;
            Object primary = entry.getKey().getPrimaryKey();
            if (!configured.contains(primary)) continue;
            variants.computeIfAbsent(primary, ignored -> new ArrayList<>())
                    .add(new AvailableKey(entry.getKey(), entry.getLongValue()));
        }
        return variants;
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
        boolean fuzzy = fuzzy();
        FuzzyMode mode = fuzzy ? fuzzyMode() : FuzzyMode.IGNORE_ALL;
        for (int slot = 0; slot < host.getConfig().size(); slot++) {
            GenericStack configured = host.getConfig().getStack(slot);
            if (configured == null) continue;
            if (configured.what().equals(candidate)) return true;
            if (fuzzy && configured.what().fuzzyEquals(candidate, mode)) return true;
        }
        return false;
    }

    private boolean fuzzy() {
        return host.getUpgrades().isInstalled(AEItems.FUZZY_CARD);
    }

    private FuzzyMode fuzzyMode() {
        return host.getConfigManager().getSetting(Settings.FUZZY_MODE);
    }

    private KeyCounter available(boolean fresh) {
        return fresh ? storage().getAvailableStacks() : grid.getStorageService().getCachedInventory();
    }

    private MEStorage storage() {
        return grid.getStorageService().getInventory();
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    private static Actionable actionable(boolean simulate) {
        return simulate ? Actionable.SIMULATE : Actionable.MODULATE;
    }
}
