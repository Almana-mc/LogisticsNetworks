package me.almana.logisticsnetworks.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

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

    public LogisticsNetwork(UUID id) {
        this(id, "Network-" + id.toString().substring(0, 6));
    }

    public LogisticsNetwork(UUID id, String name) {
        this.id = id;
        this.name = name;
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
        }
    }

    public void removeNode(UUID nodeUuid) {
        nodeUuids.remove(nodeUuid);
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
}
