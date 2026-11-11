package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WornWrench;
import net.minecraft.resources.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

public enum LogisticsNodeEntityProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "logistics_node_entity");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!WornWrench.held(accessor.getPlayer()).isEmpty()) {
            tooltip.remove(JadeIds.CORE_MOD_NAME);
            return;
        }
        tooltip.clear();
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
