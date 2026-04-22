package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

public enum LogisticsNodeEntityProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Logisticsnetworks.MOD_ID, "logistics_node_entity");

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
    }

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
    public ResourceLocation getUid() {
        return UID;
    }
}
