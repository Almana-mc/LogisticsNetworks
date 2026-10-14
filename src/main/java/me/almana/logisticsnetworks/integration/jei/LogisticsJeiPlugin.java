package me.almana.logisticsnetworks.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.screen.ClipboardScreen;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.NodeGraphScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class LogisticsJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID,
            "jei_plugin");
    private static final FilterGhostIngredientHandler FILTER_GHOST_HANDLER = new FilterGhostIngredientHandler();
    private static final NodeGhostIngredientHandler<NodeScreen> NODE_GHOST_HANDLER = new NodeGhostIngredientHandler<>();

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, FILTER_GHOST_HANDLER);
        registration.addGhostIngredientHandler(NodeScreen.class, NODE_GHOST_HANDLER);
        registration.addGhostIngredientHandler(NodeGraphScreen.class, new NodeGhostIngredientHandler<>());
        registration.addGhostIngredientHandler(ClipboardScreen.class, new NodeGhostIngredientHandler<>());
        registration.addGuiContainerHandler(FilterScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
                    @Override
                    public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(FilterScreen screen) {
                        return screen.getExtraAreas();
                    }
                });
        registration.addGuiContainerHandler(NodeGraphScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
                    @Override
                    public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(NodeGraphScreen screen) {
                        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
                        return java.util.List.of(new net.minecraft.client.renderer.Rect2i(0, 0,
                                window.getGuiScaledWidth(), window.getGuiScaledHeight()));
                    }
                });
    }
}

