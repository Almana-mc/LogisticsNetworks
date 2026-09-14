package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import me.almana.logisticsnetworks.integration.storage.StorageAccess;
import me.almana.logisticsnetworks.integration.storage.StorageAction;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import me.almana.logisticsnetworks.integration.storage.CraftingBatch;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AE2StorageAccess implements StorageAccess {

    record GridAccess(IGrid grid, IGridNode node) {
    }

    private final StorageLink link;
    private final IGrid grid;
    private final IGridNode node;

    private AE2StorageAccess(StorageLink link, GridAccess access) {
        this.link = link;
        this.grid = access.grid();
        this.node = access.node();
    }

    @Nullable
    static AE2StorageAccess resolve(ServerLevel callerLevel, StorageLink link) {
        if (link.backend() != StorageBackend.AE2) return null;
        GridAccess access = resolveGridAccess(callerLevel, link);
        return access == null ? null : new AE2StorageAccess(link, access);
    }

    @Nullable
    static GridAccess resolveGridAccess(ServerLevel callerLevel, StorageLink link) {
        ServerLevel targetLevel = callerLevel.getServer().getLevel(link.position().dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(link.position().pos())) return null;
        BlockEntity blockEntity = targetLevel.getBlockEntity(link.position().pos());
        if (!(blockEntity instanceof IInWorldGridNodeHost host)) return null;
        IGridNode gridNode = host.getGridNode(null);
        if (gridNode == null) {
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                gridNode = host.getGridNode(direction);
                if (gridNode != null) break;
            }
        }
        if (gridNode == null || !gridNode.isActive()) return null;
        return new GridAccess(gridNode.getGrid(), gridNode);
    }

    @Override
    public StorageLink link() {
        return link;
    }

    @Override
    public boolean isValid() {
        return node.isActive() && node.getGrid() == grid;
    }

    @Override
    public boolean allows(ServerPlayer player, StorageAction action) {
        return true;
    }

    @Override
    public long count(ItemStack pattern) {
        if (pattern.isEmpty()) return 0;
        long total = 0;
        for (AEItemKey key : matchingKeys(pattern)) {
            total = saturatingAdd(total, storage().getAvailableStacks().get(key));
        }
        return total;
    }

    @Override
    public long count(Item item) {
        long total = 0;
        for (AEItemKey key : matchingKeys(item)) {
            total = saturatingAdd(total, storage().getAvailableStacks().get(key));
        }
        return total;
    }

    @Override
    public List<ItemStack> extract(ItemStack pattern, int amount, ServerPlayer player) {
        if (pattern.isEmpty() || amount <= 0 || !isValid()) return List.of();
        List<ItemStack> extracted = new ArrayList<>();
        int remaining = amount;
        IActionSource source = IActionSource.ofPlayer(player);
        for (AEItemKey key : matchingKeys(pattern)) {
            if (remaining <= 0) break;
            long moved = storage().extract(key, remaining, Actionable.MODULATE, source);
            addStacks(extracted, key, moved);
            remaining -= (int) Math.min(moved, remaining);
        }
        return extracted;
    }

    @Override
    public ItemStack extractOne(Item item, ServerPlayer player) {
        if (!isValid()) return ItemStack.EMPTY;
        IActionSource source = IActionSource.ofPlayer(player);
        for (AEItemKey key : matchingKeys(item)) {
            if (storage().extract(key, 1, Actionable.MODULATE, source) == 1) return key.toStack();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public long insert(ItemStack stack, long amount, ServerPlayer player) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || amount <= 0 || !isValid()) return 0;
        return storage().insert(key, amount, Actionable.MODULATE, IActionSource.ofPlayer(player));
    }

    @Override
    public Map<Item, Long> countTagged(TagKey<Item> tag) {
        Map<Item, Long> counts = new IdentityHashMap<>();
        for (Object2LongMap.Entry<AEKey> entry : storage().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey key && key.getReadOnlyStack().is(tag)) {
                counts.merge(key.getItem(), entry.getLongValue(), AE2StorageAccess::saturatingAdd);
            }
        }
        return counts;
    }

    @Override
    public Set<Item> craftableTagged(TagKey<Item> tag) {
        Set<Item> items = new HashSet<>();
        for (AEKey key : grid.getCraftingService().getCraftables(AEItemKey.filter())) {
            if (key instanceof AEItemKey itemKey && itemKey.getReadOnlyStack().is(tag)) {
                items.add(itemKey.getItem());
            }
        }
        return items;
    }

    @Override
    public boolean isCraftable(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        return key != null && isValid() && grid.getCraftingService().isCraftable(key);
    }

    @Nullable
    @Override
    public CraftingBatch startCrafting(ServerPlayer player, List<LinkedStorage.ItemRequirement> requirements,
                                       CraftingBatch.Listener listener) {
        return AE2StorageCraftingBatch.start(this, player, requirements, listener);
    }

    static void registerWrenchLinkable() {
        GridLinkables.register(Registration.WRENCH.get(), AE2LinkHandler.INSTANCE);
    }

    IGrid grid() {
        return grid;
    }

    IGridNode node() {
        return node;
    }

    private MEStorage storage() {
        return grid.getStorageService().getInventory();
    }

    private List<AEItemKey> matchingKeys(ItemStack pattern) {
        List<AEItemKey> keys = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : storage().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey key && ItemStack.isSameItem(key.toStack(), pattern)) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.comparing((AEItemKey key) -> BuiltInRegistries.ITEM.getKey(key.getItem()).toString())
                .thenComparing(Object::toString));
        return keys;
    }

    private List<AEItemKey> matchingKeys(Item item) {
        List<AEItemKey> keys = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : storage().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey key && key.getItem() == item) keys.add(key);
        }
        keys.sort(Comparator.comparing(Object::toString));
        return keys;
    }

    private static void addStacks(List<ItemStack> target, AEItemKey key, long amount) {
        while (amount > 0) {
            ItemStack unit = key.toStack();
            int count = (int) Math.min(amount, unit.getMaxStackSize());
            target.add(key.toStack(count));
            amount -= count;
        }
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }
}
