package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.SetDefaultNodeVisibilityPayload;
import net.minecraft.client.Minecraft;

public final class DefaultNodeVisibilitySync {
    private DefaultNodeVisibilitySync() {
    }

    public static void send() {
        if (Minecraft.getInstance().getConnection() != null)
            NetworkHandler.sendToServer(new SetDefaultNodeVisibilityPayload(ClientConfig.defaultNodeVisibility));
    }
}
