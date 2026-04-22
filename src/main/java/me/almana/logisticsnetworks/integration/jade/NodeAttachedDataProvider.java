package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

public enum NodeAttachedDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final String KEY_HAS_NODE = "has_node";

    private static final Identifier UID = Identifier.fromNamespaceAndPath(
            Logisticsnetworks.MOD_ID, "node_attached");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        List<LogisticsNodeEntity> nodes = accessor.getLevel().getEntitiesOfClass(
                LogisticsNodeEntity.class,
                new AABB(accessor.getPosition()).inflate(0.5));
        for (LogisticsNodeEntity node : nodes) {
            if (node.getAttachedPos().equals(accessor.getPosition()) && node.isActive()) {
                data.putBoolean(KEY_HAS_NODE, true);
                return;
            }
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
