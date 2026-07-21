package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.network.SetDefaultNodeVisibilityPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DefaultNodeVisibilitySync {

    private DefaultNodeVisibilitySync() {
    }

    public static void send() {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new SetDefaultNodeVisibilityPayload(ClientConfig.defaultNodeVisibility));
    }
}
