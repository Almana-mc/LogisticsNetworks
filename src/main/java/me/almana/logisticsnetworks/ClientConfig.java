package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.client.theme.ThemeState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientConfig {

    private static final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue maxRenderedNodesSpec = builder
            .comment("Maximum number of nodes rendered when holding a wrench. Nearest nodes are prioritized.")
            .defineInRange("maxRenderedNodes", 200, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue maxVisibleNodesSpec = builder
            .comment("Maximum number of visible node models rendered. Nearest nodes are prioritized. 0 = unlimited.")
            .defineInRange("maxVisibleNodes", 500, 0, Integer.MAX_VALUE);

    private static final List<String> THEMES = List.of(
            "light", "dark", "redstone", "nebula", "glass", "terminal", "pastel", "brutalist");

    public static final ForgeConfigSpec.ConfigValue<String> themeSpec = builder
            .comment("GUI theme for logistics node screens: light, dark, redstone, nebula, glass, terminal, pastel, brutalist")
            .define("theme", "dark", o -> o instanceof String s && THEMES.contains(s));

    static final ForgeConfigSpec SPEC = builder.build();

    public static int maxRenderedNodes = 200;
    public static int maxVisibleNodes = 500;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        refresh();
    }

    public static void refresh() {
        maxRenderedNodes = maxRenderedNodesSpec.get();
        maxVisibleNodes = maxVisibleNodesSpec.get();
        ThemeState.applyFromConfig(themeSpec.get());
    }
}
