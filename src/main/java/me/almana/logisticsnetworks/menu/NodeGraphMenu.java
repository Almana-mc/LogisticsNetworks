package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.network.SyncNetworkGraphPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;

import java.util.UUID;

public class NodeGraphMenu extends NodeMenu {
    private final GraphMenuContext graphContext;
    private final Player player;
    private SyncNetworkGraphPayload graphSnapshot;

    public NodeGraphMenu(int containerId, Inventory inventory, GraphMenuContext graphContext,
            LogisticsNodeEntity node, int selectedChannel) {
        super(Registration.NODE_GRAPH_MENU.get(), containerId, inventory, node, selectedChannel);
        this.graphContext = graphContext;
        this.player = inventory.player;
        setNodeSlotsVisible(node != null);
    }

    public NodeGraphMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, GraphMenuContext.read(buf), readSelection(buf, inventory));
    }

    private NodeGraphMenu(int containerId, Inventory inventory, GraphMenuContext context,
            NodeMenuSync.ClientNodeState state) {
        this(containerId, inventory, context, state.node(), state.selectedChannel());
    }

    private static NodeMenuSync.ClientNodeState readSelection(FriendlyByteBuf buf, Inventory inventory) {
        return buf.readBoolean() ? NodeMenuSync.read(buf, inventory.player)
                : new NodeMenuSync.ClientNodeState(-1, 0, null);
    }

    public UUID getGraphNetworkId() {
        return graphContext.networkId();
    }

    public BlockPos getComputerPos() {
        return graphContext.computerPos();
    }

    public Identifier getComputerDimension() {
        return graphContext.computerDimension();
    }

    @Override
    public GraphMenuContext getGraphContext() {
        return graphContext;
    }

    public void receiveGraph(SyncNetworkGraphPayload snapshot) {
        if (getGraphNetworkId().equals(snapshot.networkId())) graphSnapshot = snapshot;
    }

    public SyncNetworkGraphPayload getGraphSnapshot() {
        return graphSnapshot;
    }

    @Override
    public boolean stillValid(Player player) {
        return graphContext.stillValid(player);
    }

    @Override
    protected boolean hasAvailableNode() {
        return graphContext != null && graphContext.canEdit(player, getNode());
    }

    @Override
    protected boolean hasVisibleInventory() {
        return hasAvailableNode();
    }

    @Override
    public void clicked(int slotId, int dragType, ContainerInput clickType, Player player) {
        if (stillValid(player) && hasAvailableNode()) super.clicked(slotId, dragType, clickType, player);
    }
}
