package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.model.NodeModel;
import me.almana.logisticsnetworks.client.screen.ClipboardScreen;
import me.almana.logisticsnetworks.client.screen.ComputerScreen;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.MassPlacementScreen;
import me.almana.logisticsnetworks.client.screen.NodeGraphScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.client.screen.PatternSetterScreen;
import me.almana.logisticsnetworks.client.screen.RecipeCardScreen;
import me.almana.logisticsnetworks.client.screen.ServerRackScreen;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.client.tooltip.ClientFilterPreviewTooltip;
import me.almana.logisticsnetworks.client.tooltip.FilterPreviewTooltip;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.nio.file.Path;

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
            MenuScreens.register(Registration.NODE_GRAPH_MENU.get(), NodeGraphScreen::new);
            MenuScreens.register(Registration.SERVER_RACK_MENU.get(), ServerRackScreen::new);
            MenuScreens.register(Registration.RECIPE_CARD_MENU.get(), RecipeCardScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(FilterPreviewTooltip.class, ClientFilterPreviewTooltip::new);
    }

    @SubscribeEvent
    public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path root = ModList.get().getModFileById(Logisticsnetworks.MOD_ID).getFile()
                .findResource("resourcepacks/original_textures");
        Pack pack = Pack.readMetaAndCreate(Logisticsnetworks.MOD_ID + ":original_textures",
                Component.translatable("pack.logisticsnetworks.original_textures"), false,
                id -> new PathPackResources(id, root, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NodeModel.LAYER_LOCATION, NodeModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ThemeState::load);
    }
}
