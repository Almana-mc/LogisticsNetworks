package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.client.WrenchHudOverlay;
import me.almana.logisticsnetworks.client.screen.ClipboardScreen;
import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.MassPlacementScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.client.screen.PatternSetterScreen;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.render.LogisticsNodeRenderer;
import me.almana.logisticsnetworks.render.NodeModel;
import me.almana.logisticsnetworks.registration.Registration;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class LogisticsClientEvents {

    private LogisticsClientEvents() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Registration.LOGISTICS_NODE.get(), LogisticsNodeRenderer::new);
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NodeModel.LAYER_LOCATION, NodeModel::createBodyLayer);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.NODE_MENU.get(), NodeScreen::new);
        event.register(Registration.FILTER_MENU.get(), FilterScreen::new);
        event.register(Registration.CLIPBOARD_MENU.get(), ClipboardScreen::new);
        event.register(Registration.MASS_PLACEMENT_MENU.get(), MassPlacementScreen::new);
        event.register(Registration.PATTERN_SETTER_MENU.get(), PatternSetterScreen::new);
        event.register(Registration.COMPUTER_MENU.get(), ComputerScreen::new);
    }

    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ThemeState::load);
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        WrenchHudOverlay.registerKeys(event);
    }
}
