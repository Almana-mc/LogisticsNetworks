package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.model.NodeModel;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.registration.Registration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Registration.LOGISTICS_NODE.get(), LogisticsNodeRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.NODE_MENU.get(), NodeScreen::new);
        event.register(Registration.FILTER_MENU.get(), FilterScreen::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NodeModel.LAYER_LOCATION, NodeModel::createBodyLayer);
    }
}
