package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.model.NodeModel;
import me.almana.logisticsnetworks.client.screen.ClipboardScreen;
import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.MassPlacementScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.client.screen.PatternSetterScreen;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Registration.LOGISTICS_NODE.get(), LogisticsNodeRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Registration.NODE_MENU.get(), NodeScreen::new);
            MenuScreens.register(Registration.FILTER_MENU.get(), FilterScreen::new);
            MenuScreens.register(Registration.CLIPBOARD_MENU.get(), ClipboardScreen::new);
            MenuScreens.register(Registration.MASS_PLACEMENT_MENU.get(), MassPlacementScreen::new);
            MenuScreens.register(Registration.PATTERN_SETTER_MENU.get(), PatternSetterScreen::new);
            MenuScreens.register(Registration.COMPUTER_MENU.get(), ComputerScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NodeModel.LAYER_LOCATION, NodeModel::createBodyLayer);
    }
}
