package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum NodeAttachedComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(
            Logisticsnetworks.MOD_ID, "node_attached");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().getBoolean(NodeAttachedDataProvider.KEY_HAS_NODE).orElse(false)) {
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
    public Identifier getUid() {
        return UID;
    }
}
