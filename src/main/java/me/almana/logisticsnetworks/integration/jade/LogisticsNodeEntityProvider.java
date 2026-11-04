package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WornWrench;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

public enum LogisticsNodeEntityProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "logistics_node_entity");

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!WornWrench.held(accessor.getPlayer()).isEmpty()) {
            tooltip.remove(JadeIds.CORE_MOD_NAME);
            return;
        }
        tooltip.clear();
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
