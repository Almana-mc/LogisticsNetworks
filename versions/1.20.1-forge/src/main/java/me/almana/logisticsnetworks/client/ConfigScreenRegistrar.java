package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.client.screen.ModConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class ConfigScreenRegistrar {

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new ModConfigScreen(parent)
                )
        );
    }
}
