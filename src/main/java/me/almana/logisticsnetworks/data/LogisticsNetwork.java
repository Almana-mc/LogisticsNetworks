package me.almana.logisticsnetworks.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class LogisticsNetwork {

    private static final String KEY_ID = "Id";
    private static final String KEY_NAME = "Name";
    private static final String KEY_SLEEPING = "Sleeping";
    private static final String KEY_NODES = "Nodes";
    private static final String KEY_NODE_UUID = "Node";

    private final UUID id;
    private String name;
    private final Set<UUID> nodeUuids = new HashSet<>();
    private boolean sleeping = true;

    // Runtime flags
    private boolean dirty = false;
    private boolean scheduled = false;

    @SuppressWarnings("unchecked")
    private final List<NodeRef>[] itemImports = new List[9];
    @SuppressWarnings("unchecked")
    private final List<NodeRef>[] fluidImports = new List[9];
    @SuppressWarnings("unchecked")
    private final List<NodeRef>[] energyImports = new List[9];
    @SuppressWarnings("unchecked")
    private final List<NodeRef>[] chemicalImports = new List[9];
    @SuppressWarnings("unchecked")
    private final List<NodeRef>[] sourceImports = new List[9];

    private boolean cacheDirty = true;

    public LogisticsNetwork(UUID id) {
        this(id, "Network-" + id.toString().substring(0, 6));
    }

    public LogisticsNetwork(UUID id, String name) {
        this.id = id;
        this.name = name;

        for (int i = 0; i < 9; i++) {
            this.itemImports[i] = new ArrayList<>();
            this.fluidImports[i] = new ArrayList<>();
            this.energyImports[i] = new ArrayList<>();
            this.chemicalImports[i] = new ArrayList<>();
            this.sourceImports[i] = new ArrayList<>();
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_ID, id);
        tag.putString(KEY_NAME, name);
        tag.putBoolean(KEY_SLEEPING, sleeping);

        ListTag nodesTag = new ListTag();
        for (UUID uuid : nodeUuids) {
            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID(KEY_NODE_UUID, uuid);
            nodesTag.add(uuidTag);
        }
        tag.put(KEY_NODES, nodesTag);

        return tag;
    }

    public static LogisticsNetwork load(CompoundTag tag) {
        UUID id = tag.getUUID(KEY_ID);
        LogisticsNetwork network = new LogisticsNetwork(id);

        if (tag.contains(KEY_NAME)) {
            network.name = tag.getString(KEY_NAME);
        }
        if (tag.contains(KEY_SLEEPING)) {
            network.sleeping = tag.getBoolean(KEY_SLEEPING);
        }

        if (tag.contains(KEY_NODES)) {
            ListTag nodesTag = tag.getList(KEY_NODES, Tag.TAG_COMPOUND);
            for (Tag t : nodesTag) {
                if (t instanceof CompoundTag ct && ct.contains(KEY_NODE_UUID)) {
                    network.addNode(ct.getUUID(KEY_NODE_UUID));
                }
            }
        }
        return network;
    }

    public void addNode(UUID nodeUuid) {
        if (nodeUuid != null) {
            nodeUuids.add(nodeUuid);
            markCacheDirty();
        }
    }

    public void removeNode(UUID nodeUuid) {
        nodeUuids.remove(nodeUuid);
        markCacheDirty();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<UUID> getNodeUuids() {
        return Collections.unmodifiableSet(nodeUuids);
    }

    public boolean isSleeping() {
        return sleeping;
    }

    public void setSleeping(boolean sleeping) {
        this.sleeping = sleeping;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isScheduled() {
        return scheduled;
    }

    public void setScheduled(boolean scheduled) {
        this.scheduled = scheduled;
    }

    @Override
    public String toString() {
        return String.format("LogisticsNetwork{id=%s, name='%s', nodes=%d, dirty=%b, sleeping=%b}",
                id, name, nodeUuids.size(), dirty, sleeping);
    }

    public String getDebugInfo() {
        return String.format("Network %s: %d nodes [Dirty: %b, Scheduled: %b, Sleeping: %b]",
                id.toString().substring(0, 8), nodeUuids.size(), dirty, scheduled, sleeping);
    }

    public List<NodeRef>[] getItemImports() {
        return copyToUnmodifiableArray(itemImports);
    }

    public List<NodeRef>[] getFluidImports() {
        return copyToUnmodifiableArray(fluidImports);
    }

    public List<NodeRef>[] getEnergyImports() {
        return copyToUnmodifiableArray(energyImports);
    }

    public List<NodeRef>[] getChemicalImports() {
        return copyToUnmodifiableArray(chemicalImports);
    }

    public List<NodeRef>[] getSourceImports() {
        return copyToUnmodifiableArray(sourceImports);
    }

    @SuppressWarnings("unchecked")
    private List<NodeRef>[] copyToUnmodifiableArray(List<NodeRef>[] source) {
        List<NodeRef>[] clone = new List[9];
        for (int i = 0; i < 9; i++) {
            clone[i] = Collections.unmodifiableList(source[i]);
        }
        return clone;
    }

    public void markCacheDirty() {
        this.cacheDirty = true;
    }

    public boolean isCacheDirty() {
        return cacheDirty;
    }

    public void clearCacheDirty() {
        this.cacheDirty = false;
    }

    private void clearAllCaches() {
        for (int i = 0; i < 9; i++) {
            this.itemImports[i] = new ArrayList<>();
            this.fluidImports[i] = new ArrayList<>();
            this.energyImports[i] = new ArrayList<>();
            this.chemicalImports[i] = new ArrayList<>();
            this.sourceImports[i] = new ArrayList<>();
        }
    }

    public void rebuildCache(NetworkRegistry registry) {
        clearAllCaches();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (UUID nodeId : nodeUuids) {
            LogisticsNodeEntity node = null;
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(nodeId);
                if (entity instanceof LogisticsNodeEntity lne) {
                    node = lne;
                    break;
                }
            }

            if (node == null) {
                continue;
            }

            classifyNode(node);
        }
    }

    private void classifyNode(LogisticsNodeEntity node) {
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            if (i < 0 || i >= 9) {
                continue;
            }
            ChannelData ch = node.getChannel(i);
            if (ch == null || !ch.isEnabled()) {
                continue;
            }
            if (ch.getMode() != ChannelMode.IMPORT) {
                continue;
            }
            if (ch.getRedstoneMode() != RedstoneMode.ALWAYS_ON) {
                continue;
            }

            switch (ch.getType()) {
                case ITEM -> this.itemImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos()));
                case FLUID -> this.fluidImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos()));
                case ENERGY -> this.energyImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos()));
                case CHEMICAL -> {
                    if (NodeUpgradeData.hasMekanismChemicalUpgrade(node)) {
                        this.chemicalImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos()));
                    }
                }
                case SOURCE -> {
                    if (NodeUpgradeData.hasArsSourceUpgrade(node)) {
                        this.sourceImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos()));
                    }
                }
            }
        }
    }
}
