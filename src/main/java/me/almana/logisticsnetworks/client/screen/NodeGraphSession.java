package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.graph.GraphCanvas;
import me.almana.logisticsnetworks.network.SyncNetworkGraphPayload;

import java.util.UUID;

final class NodeGraphSession {
    private static NodeGraphSession current;
    private static UUID returningNetwork;
    final UUID networkId;
    final GraphCanvas canvas;
    NodeGraphScreen screen;
    SyncNetworkGraphPayload snapshot;
    boolean editorOpen;

    private NodeGraphSession(UUID networkId) {
        this.networkId = networkId;
        canvas = new GraphCanvas(key -> screen.selectVertex(key),
                (key, position) -> screen.moveVertex(key, position));
    }

    static void begin(UUID networkId) {
        current = new NodeGraphSession(networkId);
    }

    static NodeGraphSession attach(NodeGraphScreen screen, UUID networkId) {
        if (current == null || !current.networkId.equals(networkId)) begin(networkId);
        current.screen = screen;
        return current;
    }

    static void returnToComputer(UUID networkId) {
        returningNetwork = networkId;
        current = null;
    }

    static UUID takeReturningNetwork() {
        UUID networkId = returningNetwork;
        returningNetwork = null;
        return networkId;
    }

    static void clear() {
        current = null;
        returningNetwork = null;
    }
}
