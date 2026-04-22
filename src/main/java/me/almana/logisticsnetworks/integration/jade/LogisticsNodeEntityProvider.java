package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

public enum LogisticsNodeEntityProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(
            Logisticsnetworks.MOD_ID, "logistics_node_entity");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        ItemStack main = accessor.getPlayer().getMainHandItem();
        ItemStack off = accessor.getPlayer().getOffhandItem();
        if (main.getItem() instanceof WrenchItem || off.getItem() instanceof WrenchItem) {
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
