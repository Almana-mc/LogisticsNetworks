package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WornWrench;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum NodeAttachedComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "node_attached");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().getBoolean(NodeAttachedDataProvider.KEY_HAS_NODE).orElse(false)) {
            return;
        }
        if (!WornWrench.held(accessor.getPlayer()).isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("jade." + LogisticsNetworks.MOD_ID + ".node_attached"));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
