package me.almana.logisticsnetworks.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.screen.FilterScreen;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class LogisticsJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Logisticsnetworks.MOD_ID,
            "jei_plugin");
    private static final FilterGhostIngredientHandler FILTER_GHOST_HANDLER = new FilterGhostIngredientHandler();

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, FILTER_GHOST_HANDLER);
    }
}
