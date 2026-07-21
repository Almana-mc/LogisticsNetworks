package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

public enum NodeAttachedComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Logisticsnetworks.MOD_ID, "node_attached");
    private static final String KEY_HAS_NODE = "has_node";

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
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().getBoolean(KEY_HAS_NODE)) {
            return;
        }
        Player player = accessor.getPlayer();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (main.getItem() instanceof WrenchItem || off.getItem() instanceof WrenchItem) {
            return;
        }
        tooltip.add(Component.translatable("jade." + Logisticsnetworks.MOD_ID + ".node_attached"));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
