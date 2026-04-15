package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.client.screen.ModConfigScreen;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class ConfigScreenRegistrar {

    public static void register() {
        ModLoadingContext.get().getActiveContainer().registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new ModConfigScreen(parent)
        );
    }
}
