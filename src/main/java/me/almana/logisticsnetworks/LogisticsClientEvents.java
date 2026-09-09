package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.SlotNumberOverlay;
import me.almana.logisticsnetworks.client.WrenchHudOverlay;
import me.almana.logisticsnetworks.client.WrenchInputHandler;
import me.almana.logisticsnetworks.client.DefaultNodeVisibilitySync;
import me.almana.logisticsnetworks.client.screen.ClipboardScreen;
import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.MassPlacementScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.client.screen.NodeGraphScreen;
import net.neoforged.fml.ModList;
import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.client.screen.PatternSetterScreen;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.render.LogisticsNodeRenderer;
import me.almana.logisticsnetworks.registration.Registration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class LogisticsClientEvents {

    private LogisticsClientEvents() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Registration.LOGISTICS_NODE.get(), LogisticsNodeRenderer::new);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.NODE_MENU.get(), NodeScreen::new);
        event.register(Registration.NODE_GRAPH_MENU.get(), NodeGraphScreen::new);
        event.register(Registration.FILTER_MENU.get(), FilterScreen::new);
        event.register(Registration.CLIPBOARD_MENU.get(), ClipboardScreen::new);
        event.register(Registration.MASS_PLACEMENT_MENU.get(), MassPlacementScreen::new);
        event.register(Registration.PATTERN_SETTER_MENU.get(), PatternSetterScreen::new);
        event.register(Registration.COMPUTER_MENU.get(), ComputerScreen::new);
    }

    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ThemeState.load();
            reserveGraphCanvas();
            DefaultNodeVisibilitySync.send();
        });
    }

    private static void reserveGraphCanvas() {
        if (!ModList.get().isLoaded("ftblibrary")) return;
        try {
            Class<?> api = Class.forName("dev.ftb.mods.ftblibrary.api.client.FTBLibraryClientApi");
            api.getMethod("addSidebarScreenBlacklist", String[].class).invoke(api.getMethod("get").invoke(null),
                    (Object) new String[]{NodeGraphScreen.class.getName()});
        } catch (ReflectiveOperationException exception) {
            LogUtils.getLogger().debug("Unable to reserve graph canvas from FTB sidebar", exception);
        }
    }

    @SubscribeEvent
    public static void clientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        NodeGraphScreen.clearSession();
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        WrenchHudOverlay.registerKeys(event);
        SlotNumberOverlay.registerKeys(event);
        event.register(WrenchInputHandler.OPEN_COLOR_EDITOR);
        event.register(ClientControls.MODIFIER_1);
        event.register(ClientControls.MODIFIER_2);
        event.register(ClientControls.MODIFIER_3);
        event.register(ClientControls.PRIMARY_INTERACTION);
        event.register(ClientControls.SECONDARY_INTERACTION);
    }

    @SubscribeEvent
    public static void clientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        DefaultNodeVisibilitySync.send();
    }
}
