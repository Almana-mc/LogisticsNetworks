package me.almana.logisticsnetworks.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.security.Permission;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.security.SecurityHelper;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import me.almana.logisticsnetworks.integration.storage.CraftingBatch;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageAccess;
import me.almana.logisticsnetworks.integration.storage.StorageAction;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RefinedStorageAccess implements StorageAccess {

    private final StorageLink link;
    private final NetworkNode node;
    private final Network network;

    private RefinedStorageAccess(StorageLink link, NetworkNode node, Network network) {
        this.link = link;
        this.node = node;
        this.network = network;
    }

    @Nullable
    static RefinedStorageAccess resolve(ServerLevel callerLevel, StorageLink link) {
        if (link.backend() != StorageBackend.REFINED_STORAGE) return null;
        ServerLevel target = callerLevel.getServer().getLevel(link.position().dimension());
        if (target == null || !target.hasChunkAt(link.position().pos())) return null;
        NetworkNode node = findNode(target, link.position().pos());
        if (node == null || node.getNetwork() == null) return null;
        return new RefinedStorageAccess(link, node, node.getNetwork());
    }

    @Nullable
    static NetworkNode findNode(Level level, BlockPos pos) {
        var capability = RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability();
        var provider = level.getCapability(capability, pos, null);
        if (provider == null) return null;
        for (var container : provider.getContainers()) {
            NetworkNode node = container.getNode();
            if (node instanceof AbstractNetworkNode activeNode && activeNode.isActive()
                    && node.getNetwork() != null) return node;
        }
        return null;
    }

    @Override
    public StorageLink link() {
        return link;
    }

    @Override
    public boolean isValid() {
        return node.getNetwork() == network
                && (!(node instanceof AbstractNetworkNode activeNode) || activeNode.isActive());
    }

    @Override
    public boolean allows(ServerPlayer player, StorageAction action) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("refinedstorage", switch (action) {
            case EXTRACT -> "extract";
            case INSERT -> "insert";
            case AUTOCRAFT -> "autocrafting";
        });
        Permission permission = RefinedStorageApi.INSTANCE.getPermissionRegistry().get(id).orElse(null);
        return permission != null && SecurityHelper.isAllowed(player, permission, network);
    }

    @Override
    public long count(ItemStack pattern) {
        if (pattern.isEmpty() || !isValid()) return 0;
        long total = 0;
        for (ItemResource resource : matchingResources(pattern)) {
            total = saturatingAdd(total, storage().get(resource));
        }
        return total;
    }

    @Override
    public long count(Item item) {
        if (!isValid()) return 0;
        long total = 0;
        for (ItemResource resource : matchingResources(item)) {
            total = saturatingAdd(total, storage().get(resource));
        }
        return total;
    }

    @Override
    public List<ItemStack> extract(ItemStack pattern, int amount, ServerPlayer player) {
        if (pattern.isEmpty() || amount <= 0 || !isValid() || !allows(player, StorageAction.EXTRACT)) {
            return List.of();
        }
        List<ItemStack> extracted = new ArrayList<>();
        int remaining = amount;
        PlayerActor actor = new PlayerActor(player);
        for (ItemResource resource : matchingResources(pattern)) {
            if (remaining <= 0) break;
            long moved = storage().extract(resource, remaining, Action.EXECUTE, actor);
            addStacks(extracted, resource, moved);
            remaining -= (int) Math.min(moved, remaining);
        }
        return extracted;
    }

    @Override
    public ItemStack extractOne(Item item, ServerPlayer player) {
        if (!isValid() || !allows(player, StorageAction.EXTRACT)) return ItemStack.EMPTY;
        PlayerActor actor = new PlayerActor(player);
        for (ItemResource resource : matchingResources(item)) {
            if (storage().extract(resource, 1, Action.EXECUTE, actor) == 1) return resource.toItemStack();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public long insert(ItemStack stack, long amount, ServerPlayer player) {
        ResourceKey resource = resource(stack);
        if (resource == null || amount <= 0 || !isValid() || !allows(player, StorageAction.INSERT)) return 0;
        return storage().insert(resource, amount, Action.EXECUTE, new PlayerActor(player));
    }

    @Override
    public Map<Item, Long> countTagged(TagKey<Item> tag) {
        Map<Item, Long> counts = new IdentityHashMap<>();
        if (!isValid()) return counts;
        for (ResourceAmount entry : storage().getAll()) {
            if (entry.resource() instanceof ItemResource resource && resource.toItemStack().is(tag)) {
                counts.merge(resource.item(), entry.amount(), RefinedStorageAccess::saturatingAdd);
            }
        }
        return counts;
    }

    @Override
    public Set<Item> craftableTagged(TagKey<Item> tag) {
        Set<Item> items = new HashSet<>();
        if (!isValid()) return items;
        for (ResourceKey key : autocrafting().getOutputs()) {
            if (key instanceof ItemResource resource && resource.toItemStack().is(tag)) items.add(resource.item());
        }
        return items;
    }

    @Override
    public boolean isCraftable(ItemStack stack) {
        ResourceKey resource = resource(stack);
        return resource != null && isValid() && !autocrafting().getPatternsByOutput(resource).isEmpty();
    }

    @Nullable
    @Override
    public CraftingBatch startCrafting(ServerPlayer player, List<LinkedStorage.ItemRequirement> requirements,
                                       CraftingBatch.Listener listener) {
        if (!allows(player, StorageAction.AUTOCRAFT) || !allows(player, StorageAction.EXTRACT)) {
            listener.failed(me.almana.logisticsnetworks.integration.storage.StorageFailure.of(
                    me.almana.logisticsnetworks.integration.storage.StorageFailure.Reason.PERMISSION_DENIED));
            return null;
        }
        return RefinedStorageCraftingBatch.start(this, player, requirements, listener);
    }

    StorageNetworkComponent storage() {
        return network.getComponent(StorageNetworkComponent.class);
    }

    AutocraftingNetworkComponent autocrafting() {
        return network.getComponent(AutocraftingNetworkComponent.class);
    }

    @Nullable
    ResourceKey resource(ItemStack stack) {
        return RefinedStorageApi.INSTANCE.getItemResourceFactory().create(stack)
                .map(ResourceAmount::resource).orElse(null);
    }

    private List<ItemResource> matchingResources(ItemStack pattern) {
        List<ItemResource> resources = matchingResources(pattern.getItem());
        resources.removeIf(resource -> !ItemStack.isSameItem(resource.toItemStack(), pattern));
        return resources;
    }

    private List<ItemResource> matchingResources(Item item) {
        List<ItemResource> resources = new ArrayList<>();
        for (ResourceAmount entry : storage().getAll()) {
            if (entry.resource() instanceof ItemResource resource && resource.item() == item) resources.add(resource);
        }
        resources.sort(Comparator.comparing((ItemResource resource) ->
                        BuiltInRegistries.ITEM.getKey(resource.item()).toString())
                .thenComparing(Object::toString));
        return resources;
    }

    private static void addStacks(List<ItemStack> target, ItemResource resource, long amount) {
        ItemStack unit = resource.toItemStack();
        while (amount > 0) {
            int count = (int) Math.min(amount, unit.getMaxStackSize());
            target.add(resource.toItemStack(count));
            amount -= count;
        }
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }
}
