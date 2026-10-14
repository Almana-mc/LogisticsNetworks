package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.menu.NodeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class NodeScreen extends NodeEditorScreen<NodeMenu> {

    public NodeScreen(NodeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
