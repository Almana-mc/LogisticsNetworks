package me.almana.logisticsnetworks.integration.buildinggadgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.logic.NodePlacementHelper;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class BuildingGadgetsCompat {
    private static final Map<Object, Map<String, Optional<UUID>>> PASTE_NETWORKS = new WeakHashMap<>();
    private static boolean cutProbe;

    private BuildingGadgetsCompat() {
    }

    public static boolean isCutProbe() {
        return cutProbe;
    }

    public static void setCutProbe(boolean active) {
        cutProbe = active;
    }

    @Nullable
    public static CompoundTag captureMove(ServerLevel level, BlockPos pos) {
        LogisticsNodeEntity node = findNode(level, pos);
        if (node == null) return null;

        CompoundTag payload = new CompoundTag();
        payload.putString("kind", NodeTransit.KIND_MOVE);
        payload.store("uuid", UUIDUtil.CODEC, node.getUUID());
        payload.put("state", node.saveNodeState());

        // Keep membership; empty networks delete
        if (node.getNetworkId() != null) NetworkRegistry.get(level).invalidateNetwork(node.getNetworkId());
        node.discard();
        return payload;
    }

    public static Map<BlockPos, CompoundTag> captureCopy(ServerPlayer player, BlockPos start, BlockPos end) {
        AABB area = AABB.encapsulatingFullBlocks(start, end);
        if (area.getXsize() > 501 || area.getYsize() > 501 || area.getZsize() > 501) return Map.of();
        Map<BlockPos, CompoundTag> configs = new HashMap<>();
        for (LogisticsNodeEntity node : player.level().getEntitiesOfClass(LogisticsNodeEntity.class, area,
                node -> node.isActive() && node.isOwnedBy(player))) {
            BlockPos attached = node.getAttachedPos();
            if (area.contains(Vec3.atCenterOf(attached))) {
                configs.put(attached.subtract(start), NodeClipboardConfig.fromNode(node).save(player.registryAccess()));
            }
        }
        if (NodeTransit.fits(configs.values())) return configs;
        player.sendSystemMessage(Component.translatable("message.logisticsnetworks.buildinggadgets.copy_too_large"));
        return Map.of();
    }

    @Nullable
    public static CompoundTag chargeCopy(ServerPlayer player, CompoundTag config, Object paste,
            Predicate<List<ItemStack>> pay) {
        NodeClipboardConfig clipboard = loadSanitized(config, player.registryAccess());
        if (clipboard == null) return null;

        boolean paid = !player.isCreative();
        List<ItemStack> cost = cost(clipboard);
        if (paid && !pay.test(cost)) {
            player.sendOverlayMessage(Component.translatable(
                    "message.logisticsnetworks.buildinggadgets.missing_items", describe(cost)));
            return null;
        }

        CompoundTag payload = new CompoundTag();
        payload.putString("kind", NodeTransit.KIND_COPY);
        payload.store("owner", UUIDUtil.CODEC, player.getUUID());
        payload.putBoolean("paid", paid);
        payload.put("config", clipboard.save(player.registryAccess()));
        UUID network = resolveNetwork(player, clipboard, paste);
        if (network != null) payload.store("network", UUIDUtil.CODEC, network);
        return payload;
    }

    public static List<ItemStack> pasteCost(CompoundTag config, HolderLookup.Provider registries) {
        NodeClipboardConfig clipboard = loadSanitized(config, registries);
        return clipboard == null ? List.of() : cost(clipboard);
    }

    @Nullable
    private static NodeClipboardConfig loadSanitized(CompoundTag config, HolderLookup.Provider registries) {
        NodeClipboardConfig clipboard = NodeClipboardConfig.load(config, registries);
        if (clipboard == null || !clipboard.isStructurallyValid()) return null;
        clipboard.stripUpgradeComponents();
        return clipboard;
    }

    private static Component describe(List<ItemStack> cost) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < cost.size(); i++) {
            if (i > 0) text.append(", ");
            text.append(cost.get(i).getCount() + "x ").append(cost.get(i).getHoverName());
        }
        return text;
    }

    public static void spawn(ServerLevel level, BlockPos pos, CompoundTag payload) {
        if (NodeTransit.KIND_MOVE.equals(payload.getStringOr("kind", ""))) {
            spawnMoved(level, pos, payload);
        } else {
            spawnCopied(level, pos, payload);
        }
    }

    private static void spawnMoved(ServerLevel level, BlockPos pos, CompoundTag payload) {
        if (NodePlacementHelper.validatePlacement(level, pos, true) != NodePlacementHelper.ValidationResult.OK) {
            dropMoved(level, pos, payload);
            return;
        }
        LogisticsNodeEntity node = Registration.LOGISTICS_NODE.get().create(level, EntitySpawnReason.LOAD);
        if (node == null) return;
        node.loadNodeState(payload.getCompoundOrEmpty("state"));
        node.setPos(Vec3.atBottomCenterOf(pos));
        node.setAttachedPos(pos);

        UUID original = payload.read("uuid", UUIDUtil.CODEC).orElseThrow();
        if (level.getEntity(original) == null) node.setUUID(original);
        NetworkRegistry registry = NetworkRegistry.get(level);
        UUID networkId = node.getNetworkId();
        if (networkId != null && registry.getNetwork(networkId) == null) {
            node.setNetworkId(null);
            node.setNetworkName("");
            networkId = null;
        }
        if (!level.addFreshEntity(node)) {
            dropMoved(level, pos, payload);
            return;
        }
        if (networkId != null) registry.addNodeToNetwork(networkId, node.getUUID());
    }

    public static void dropMoved(ServerLevel level, BlockPos pos, CompoundTag payload) {
        LogisticsNodeEntity node = Registration.LOGISTICS_NODE.get().create(level, EntitySpawnReason.LOAD);
        if (node == null) return;
        node.loadNodeState(payload.getCompoundOrEmpty("state"));
        node.setPos(Vec3.atBottomCenterOf(pos));
        UUID original = payload.read("uuid", UUIDUtil.CODEC).orElseThrow();
        if (node.getNetworkId() != null && level.getEntity(original) == null) {
            NetworkRegistry.get(level).removeNodeFromNetwork(node.getNetworkId(), original);
        }
        if (Config.dropNodeItem) node.spawnAtLocation(level, Registration.LOGISTICS_NODE_ITEM.get());
        node.dropUpgrades();
    }

    public static void release(ServerLevel level, BlockPos pos, CompoundTag payload) {
        if (NodeTransit.KIND_MOVE.equals(payload.getStringOr("kind", ""))) {
            dropMoved(level, pos, payload);
        } else {
            refundCopy(level, pos, payload);
        }
    }

    private static void refundCopy(ServerLevel level, BlockPos pos, CompoundTag payload) {
        NodeClipboardConfig config = NodeClipboardConfig.load(payload.getCompoundOrEmpty("config"), level.registryAccess());
        if (config == null || !payload.getBooleanOr("paid", false)) return;
        for (ItemStack stack : cost(config)) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    private static void spawnCopied(ServerLevel level, BlockPos pos, CompoundTag payload) {
        NodeClipboardConfig config = NodeClipboardConfig.load(payload.getCompoundOrEmpty("config"), level.registryAccess());
        if (config == null) return;
        LogisticsNodeEntity node = NodePlacementHelper.validatePlacement(level, pos)
                == NodePlacementHelper.ValidationResult.OK
                ? NodePlacementHelper.placeNode(level, pos, payload.read("owner", UUIDUtil.CODEC).orElse(null))
                : null;
        if (node == null) {
            refundCopy(level, pos, payload);
            return;
        }
        config.applyToNode(node);
        Optional<UUID> networkId = payload.read("network", UUIDUtil.CODEC);
        if (networkId.isEmpty()) return;
        NetworkRegistry registry = NetworkRegistry.get(level);
        LogisticsNetwork network = registry.getNetwork(networkId.get());
        if (network != null) NodeClipboardConfig.joinNetwork(node, registry, network);
    }

    private static List<ItemStack> cost(NodeClipboardConfig clipboard) {
        List<ItemStack> cost = new ArrayList<>();
        cost.add(new ItemStack(Registration.LOGISTICS_NODE_ITEM.get()));
        for (NodeClipboardConfig.RequiredItem required : clipboard.getRequiredItemsPreview()) {
            cost.add(required.stack().copyWithCount(required.count()));
        }
        return cost;
    }

    @Nullable
    private static UUID resolveNetwork(ServerPlayer player, NodeClipboardConfig clipboard, Object paste) {
        String source = clipboard.getNetworkId() != null ? clipboard.getNetworkId().toString()
                : clipboard.getNetworkName();
        // One network per source
        return PASTE_NETWORKS.computeIfAbsent(paste, key -> new HashMap<>())
                .computeIfAbsent(source, key -> Optional.ofNullable(clipboard.resolveNetworkId(player)))
                .orElse(null);
    }

    @Nullable
    private static LogisticsNodeEntity findNode(ServerLevel level, BlockPos pos) {
        List<LogisticsNodeEntity> nodes = level.getEntitiesOfClass(LogisticsNodeEntity.class,
                new AABB(pos).inflate(0.1), node -> node.getAttachedPos().equals(pos));
        return nodes.isEmpty() ? null : nodes.get(0);
    }
}
