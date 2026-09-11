package me.almana.logisticsnetworks.integration.storage;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.ae2.AE2StorageAdapter;
import me.almana.logisticsnetworks.integration.refinedstorage.RefinedStorageAdapter;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LinkedStorage {

    private static final Map<StorageBackend, StorageAdapter> ADAPTERS = new EnumMap<>(StorageBackend.class);

    public record PatternEntry(ItemStack item, int amount) {
    }

    public record UpgradeEntry(ItemStack item, long stored, boolean craftable) {
    }

    public record ItemRequirement(ItemStack stack, int count) {
    }

    public interface SupplyOperation {
        boolean stillValid();

        boolean commit();

        void failed(Component detail);
    }

    public enum UpgradeInstallResult {
        INSTALLED,
        CRAFTING,
        DUPLICATE,
        PENDING,
        NO_SLOT,
        UNAVAILABLE
    }

    public enum NodePlacementRequestResult {
        QUEUED,
        DUPLICATE,
        UNAVAILABLE
    }

    public enum SupplyRequestResult {
        COMPLETED,
        QUEUED,
        DUPLICATE,
        UNAVAILABLE
    }

    private LinkedStorage() {
    }

    public static void initialize() {
        ADAPTERS.clear();
        if (ModList.get().isLoaded(StorageBackend.AE2.modId())) {
            ADAPTERS.put(StorageBackend.AE2, AE2StorageAdapter.INSTANCE);
        }
        if (ModList.get().isLoaded(StorageBackend.REFINED_STORAGE.modId())) {
            ADAPTERS.put(StorageBackend.REFINED_STORAGE, RefinedStorageAdapter.INSTANCE);
        }
        ADAPTERS.values().forEach(StorageAdapter::registerWrenchLinkable);
    }

    public static boolean isBackendLoaded(StorageBackend backend) {
        return ADAPTERS.containsKey(backend);
    }

    @Nullable
    public static StorageBackend detect(Level level, BlockPos pos) {
        for (StorageBackend backend : StorageBackend.values()) {
            StorageAdapter adapter = ADAPTERS.get(backend);
            if (adapter != null && adapter.detects(level, pos)) return backend;
        }
        return null;
    }

    @Nullable
    public static StorageAccess resolve(ServerLevel level, StorageLink link) {
        StorageAdapter adapter = ADAPTERS.get(link.backend());
        return adapter == null ? null : adapter.resolve(level, link);
    }

    public static InterfaceStorageResolution resolveInterface(ServerLevel level, BlockPos pos,
            @Nullable Direction direction) {
        for (StorageAdapter adapter : ADAPTERS.values()) {
            InterfaceStorageResolution resolution = adapter.resolveInterface(level, pos, direction);
            if (resolution.status() != InterfaceStorageResolution.Status.UNSUPPORTED) return resolution;
        }
        return InterfaceStorageResolution.unsupported();
    }

    public static boolean isAccessible(ServerLevel level, StorageLink link) {
        return resolve(level, link) != null;
    }

    public static boolean isPattern(ItemStack stack, Level level) {
        for (StorageAdapter adapter : ADAPTERS.values()) {
            if (adapter.isPattern(stack, level)) return true;
        }
        return false;
    }

    public static List<PatternEntry> readPatternInputs(ItemStack stack, Level level) {
        for (StorageAdapter adapter : ADAPTERS.values()) {
            if (adapter.isPattern(stack, level)) return adapter.readPatternInputs(stack, level);
        }
        return List.of();
    }

    public static List<PatternEntry> readPatternOutputs(ItemStack stack, Level level) {
        for (StorageAdapter adapter : ADAPTERS.values()) {
            if (adapter.isPattern(stack, level)) return adapter.readPatternOutputs(stack, level);
        }
        return List.of();
    }

    @Nullable
    public static StorageLink findAccessibleLink(ServerPlayer player, @Nullable StorageLink preferred) {
        Inventory inventory = player.getInventory();
        if (preferred != null) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                StorageLink link = linked(inventory.getItem(slot));
                if (preferred.equals(link) && isAccessible(player.serverLevel(), link)) return link;
            }
        }
        int selected = inventory.selected;
        StorageLink selectedLink = linked(inventory.getItem(selected));
        if (selectedLink != null && isAccessible(player.serverLevel(), selectedLink)) return selectedLink;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) continue;
            StorageLink link = linked(inventory.getItem(slot));
            if (link != null && isAccessible(player.serverLevel(), link)) return link;
        }
        for (int slot = 9; slot < inventory.getContainerSize(); slot++) {
            StorageLink link = linked(inventory.getItem(slot));
            if (link != null && isAccessible(player.serverLevel(), link)) return link;
        }
        return null;
    }

    public static long count(ServerLevel level, StorageLink link, ItemStack pattern) {
        StorageAccess access = resolve(level, link);
        return access == null ? 0 : access.count(pattern);
    }

    public static int extract(ServerPlayer player, StorageLink link, ItemStack pattern, int amount) {
        StorageAccess access = resolve(player.serverLevel(), link);
        return access == null ? 0 : StorageInventory.count(access.extract(pattern, amount, player));
    }

    public static void returnItems(ServerPlayer player, @Nullable StorageLink link, List<ItemStack> stacks) {
        StorageAccess access = link == null ? null : resolve(player.serverLevel(), link);
        if (access == null) StorageInventory.returnToPlayer(player, stacks);
        else StorageInventory.returnToStorageOrPlayer(access, player, stacks);
    }

    public static List<UpgradeEntry> listUpgrades(ServerPlayer player, StorageLink link,
                                                   List<ItemStack> installed) {
        return StorageUpgradeRequests.list(player, link, installed);
    }

    public static UpgradeInstallResult installUpgrade(ServerPlayer player, LogisticsNodeEntity node,
                                                       StorageLink link, int preferredSlot, Item upgrade) {
        return StorageUpgradeRequests.install(player, node, link, preferredSlot, upgrade);
    }

    public static boolean isUpgradeCraftingPending(LogisticsNodeEntity node) {
        return StorageUpgradeRequests.isPending(node);
    }

    public static NodePlacementRequestResult requestNodePlacement(ServerPlayer player, GlobalPos target,
                                                                  StorageLink link, boolean renderVisible) {
        return StorageNodePlacementRequests.request(player, target, link, renderVisible);
    }

    public static boolean isNodePlacementQueued(GlobalPos target) {
        return StorageNodePlacementRequests.isQueued(target);
    }

    public static SupplyRequestResult requestSupply(UUID requestId, ServerPlayer player, StorageLink link,
                                                     List<ItemRequirement> requirements, int protectedSlot,
                                                     Component subject, SupplyOperation operation) {
        return StorageSupplyRequests.request(requestId, player, link, requirements, protectedSlot, subject, operation);
    }

    public static boolean isSupplyPending(UUID requestId) {
        return StorageSupplyRequests.isPending(requestId);
    }

    public static void tickCraftingRequests(MinecraftServer server) {
        StorageUpgradeRequests.tick(server);
        StorageNodePlacementRequests.tick();
        StorageSupplyRequests.tick();
    }

    public static void stopCraftingRequests() {
        StorageUpgradeRequests.stop();
        StorageNodePlacementRequests.stop();
        StorageSupplyRequests.stop();
    }

    public static void restoreCraftingRequests(ServerPlayer player) {
        StorageUpgradeRequests.deliverNotices(player);
        StorageNodePlacementRequests.restore(player);
    }

    public static int findUpgradeSlot(List<ItemStack> upgrades, int preferredSlot, Item upgrade) {
        for (ItemStack installed : upgrades) {
            if (!installed.isEmpty() && installed.is(upgrade)) return -1;
        }
        if (preferredSlot >= 0 && preferredSlot < upgrades.size() && upgrades.get(preferredSlot).isEmpty()) {
            return preferredSlot;
        }
        for (int slot = 0; slot < upgrades.size(); slot++) {
            if (upgrades.get(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static StorageLink linked(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem ? WrenchItem.getStorageLink(stack) : null;
    }
}
