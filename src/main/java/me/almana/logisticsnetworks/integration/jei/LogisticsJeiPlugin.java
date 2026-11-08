package me.almana.logisticsnetworks.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import me.almana.logisticsnetworks.client.screen.ClipboardScreen;
import me.almana.logisticsnetworks.client.screen.NodeScreen;
import me.almana.logisticsnetworks.client.screen.NodeGraphScreen;
import me.almana.logisticsnetworks.client.screen.RecipeCardScreen;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.List;

@JeiPlugin
public class LogisticsJeiPlugin implements IModPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID,
            "jei_plugin");
    private static final FilterGhostIngredientHandler FILTER_GHOST_HANDLER = new FilterGhostIngredientHandler();
    private static final NodeGhostIngredientHandler<NodeScreen> NODE_GHOST_HANDLER = new NodeGhostIngredientHandler<>();

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, FILTER_GHOST_HANDLER);
        registration.addGhostIngredientHandler(NodeScreen.class, NODE_GHOST_HANDLER);
        registration.addGhostIngredientHandler(NodeGraphScreen.class, new NodeGhostIngredientHandler<>());
        registration.addGhostIngredientHandler(ClipboardScreen.class, new NodeGhostIngredientHandler<>());
        registration.addGhostIngredientHandler(RecipeCardScreen.class, new RecipeCardGhostHandler());
        registration.addGuiContainerHandler(NodeGraphScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(NodeGraphScreen screen) {
                var window = Minecraft.getInstance().getWindow();
                return List.of(new Rect2i(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight()));
            }
        });
        registration.addGuiContainerHandler(FilterScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(FilterScreen screen) {
                return screen.getExtraAreas();
            }
        });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new RecipeCardTransferHandler());
    }
}
