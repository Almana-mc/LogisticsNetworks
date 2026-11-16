package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.SetDefaultNodeVisibilityPayload;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public final class DefaultNodeVisibilitySync {

    private DefaultNodeVisibilitySync() {
    }

    public static void send() {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        NetworkHandler.sendToServer(new SetDefaultNodeVisibilityPayload(ClientConfig.defaultNodeVisibility));
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        send();
    }
}
