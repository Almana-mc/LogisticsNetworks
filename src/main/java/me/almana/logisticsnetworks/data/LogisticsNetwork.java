package me.almana.logisticsnetworks.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class LogisticsNetwork {

    private static final String KEY_ID = "Id";
    private static final String KEY_NAME = "Name";
    private static final String KEY_SLEEPING = "Sleeping";
    private static final String KEY_NODES = "Nodes";
    private static final String KEY_NODE_UUID = "Node";
    private static final String KEY_OWNER_UUID = "OwnerUUID";
    private static final String KEY_CHANNEL_NAMES = "ChannelNames";

    private final UUID id;
    private String name;
    private UUID ownerUuid;
    private final Set<UUID> nodeUuids = new HashSet<>();
    private boolean sleeping = true;

    // Runtime flags
    private boolean dirty = false;
    private boolean scheduled = false;

    private final String[] channelNames = new String[9];

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

    // Prebuilt unmodifiable views, rebuilt on cache rebuild
    private List<NodeRef>[] itemImportsView;
    private List<NodeRef>[] fluidImportsView;
    private List<NodeRef>[] energyImportsView;
    private List<NodeRef>[] chemicalImportsView;
    private List<NodeRef>[] sourceImportsView;

    // Per-node derived state, rebuilt on cache rebuild
    private List<UUID> sortedUuids = Collections.emptyList();
    private final Map<UUID, ResourceKey<Level>> nodeDimensions = new HashMap<>();
    private final Map<UUID, Boolean> dimensionalCache = new HashMap<>();
    private final Map<UUID, Integer> tierCache = new HashMap<>();

    private boolean cacheDirty = true;
    private long generation;

    public LogisticsNetwork(UUID id) {
        this(id, "Network-" + id.toString().substring(0, 6));
    }

    public LogisticsNetwork(UUID id, String name) {
        this.id = id;
        this.name = name;

        for (int i = 0; i < 9; i++) {
            this.channelNames[i] = "";
            this.itemImports[i] = new ArrayList<>();
            this.fluidImports[i] = new ArrayList<>();
            this.energyImports[i] = new ArrayList<>();
            this.chemicalImports[i] = new ArrayList<>();
            this.sourceImports[i] = new ArrayList<>();
        }
        rebuildViews();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_ID, id);
        tag.putString(KEY_NAME, name);
        tag.putBoolean(KEY_SLEEPING, sleeping);
        if (ownerUuid != null) {
            tag.putUUID(KEY_OWNER_UUID, ownerUuid);
        }

        ListTag nodesTag = new ListTag();
        for (UUID uuid : nodeUuids) {
            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID(KEY_NODE_UUID, uuid);
            nodesTag.add(uuidTag);
        }
        tag.put(KEY_NODES, nodesTag);

        ListTag channelNamesTag = new ListTag();
        for (int i = 0; i < 9; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", channelNames[i]);
            channelNamesTag.add(entry);
        }
        tag.put(KEY_CHANNEL_NAMES, channelNamesTag);

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
        if (tag.contains(KEY_OWNER_UUID)) {
            network.ownerUuid = tag.getUUID(KEY_OWNER_UUID);
        }

        if (tag.contains(KEY_NODES)) {
            ListTag nodesTag = tag.getList(KEY_NODES, Tag.TAG_COMPOUND);
            for (Tag t : nodesTag) {
                if (t instanceof CompoundTag ct && ct.contains(KEY_NODE_UUID)) {
                    network.addNode(ct.getUUID(KEY_NODE_UUID));
                }
            }
        }
        if (tag.contains(KEY_CHANNEL_NAMES, Tag.TAG_LIST)) {
            ListTag namesTag = tag.getList(KEY_CHANNEL_NAMES, Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(namesTag.size(), 9); i++) {
                network.channelNames[i] = namesTag.getCompound(i).getString("Name");
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

    public String getChannelName(int index) {
        if (index >= 0 && index < 9) return channelNames[index];
        return "";
    }

    public void setChannelName(int index, String name) {
        if (index >= 0 && index < 9) {
            this.channelNames[index] = name == null ? "" : name;
        }
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
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
        return itemImportsView;
    }

    public List<NodeRef>[] getFluidImports() {
        return fluidImportsView;
    }

    public List<NodeRef>[] getEnergyImports() {
        return energyImportsView;
    }

    public List<NodeRef>[] getChemicalImports() {
        return chemicalImportsView;
    }

    public List<NodeRef>[] getSourceImports() {
        return sourceImportsView;
    }

    public List<UUID> getSortedUuids() {
        return sortedUuids;
    }

    public ResourceKey<Level> getNodeDimension(UUID nodeId) {
        return nodeDimensions.get(nodeId);
    }

    public Map<UUID, Boolean> getDimensionalCache() {
        return dimensionalCache;
    }

    public Map<UUID, Integer> getTierCache() {
        return tierCache;
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
        this.generation++;
    }

    public boolean isCacheDirty() {
        return cacheDirty;
    }

    public long getGeneration() {
        return generation;
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
        nodeDimensions.clear();
        dimensionalCache.clear();
        tierCache.clear();
    }

    private void rebuildViews() {
        itemImportsView = copyToUnmodifiableArray(itemImports);
        fluidImportsView = copyToUnmodifiableArray(fluidImports);
        energyImportsView = copyToUnmodifiableArray(energyImports);
        chemicalImportsView = copyToUnmodifiableArray(chemicalImports);
        sourceImportsView = copyToUnmodifiableArray(sourceImports);
    }

    private void sortImportsByPriority() {
        for (int i = 0; i < 9; i++) {
            sortByPriority(itemImports[i]);
            sortByPriority(fluidImports[i]);
            sortByPriority(energyImports[i]);
            sortByPriority(chemicalImports[i]);
            sortByPriority(sourceImports[i]);
        }
    }

    private static void sortByPriority(List<NodeRef> refs) {
        if (refs.size() > 1) {
            refs.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        }
    }

    private static LogisticsNodeEntity findNode(MinecraftServer server, UUID nodeId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(nodeId) instanceof LogisticsNodeEntity node) {
                return node;
            }
        }
        return null;
    }

    public void rebuildCache(NetworkRegistry registry) {
        clearAllCaches();
        sortedUuids = new ArrayList<>(nodeUuids);
        sortedUuids.sort(null);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            rebuildViews();
            return;
        }

        List<LogisticsNodeEntity> resolved = new ArrayList<>();

        for (UUID nodeId : sortedUuids) {
            LogisticsNodeEntity node = findNode(server, nodeId);
            if (node == null) {
                continue;
            }

            nodeDimensions.put(nodeId, node.level().dimension());
            dimensionalCache.put(nodeId, NodeUpgradeData.hasDimensionalUpgrade(node));
            tierCache.put(nodeId, NodeUpgradeData.getUpgradeTier(node));

            for (int i = 0; i < 9; i++) {
                if (channelNames[i].isEmpty()) {
                    ChannelData ch = node.getChannel(i);
                    if (ch != null && !ch.getName().isEmpty()) {
                        channelNames[i] = ch.getName();
                    }
                }
            }

            resolved.add(node);
            classifyNode(node);
        }

        sortImportsByPriority();
        rebuildViews();

        for (LogisticsNodeEntity node : resolved) {
            for (int i = 0; i < 9; i++) {
                ChannelData ch = node.getChannel(i);
                if (ch != null) {
                    ch.setName(channelNames[i]);
                }
            }
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

            int priority = ch.getPriority();
            switch (ch.getType()) {
                case ITEM -> this.itemImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos(), priority));
                case FLUID -> this.fluidImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos(), priority));
                case ENERGY -> this.energyImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos(), priority));
                case CHEMICAL -> {
                    if (NodeUpgradeData.hasMekanismChemicalUpgrade(node)) {
                        this.chemicalImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos(), priority));
                    }
                }
                case SOURCE -> {
                    if (NodeUpgradeData.hasArsSourceUpgrade(node)) {
                        this.sourceImports[i].add(new NodeRef(node.getUUID(), node.getAttachedPos(), priority));
                    }
                }
            }
        }
    }
}
