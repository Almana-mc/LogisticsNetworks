package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.client.theme.ThemeState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public class ClientConfig {

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue defaultNodeVisibilitySpec = builder
            .comment("Whether newly placed nodes should be visible by default.")
            .define("defaultNodeVisibility", true);

    public static final ModConfigSpec.IntValue maxRenderedNodesSpec = builder
            .comment("Maximum number of nodes rendered when holding a wrench. Nearest nodes are prioritized.")
            .defineInRange("maxRenderedNodes", 200, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue maxVisibleNodesSpec = builder
            .comment("Maximum number of visible node models rendered. Nearest nodes are prioritized. 0 = unlimited.")
            .defineInRange("maxVisibleNodes", 500, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue connectedNodeTexturesSpec = builder
            .comment("Render logistics nodes as connected textures when neighboring nodes are visible.")
            .define("connectedNodeTextures", true);

    public static final ModConfigSpec.BooleanValue showTransferVisualsSpec = builder
            .comment("Show resource transfer particles while holding a wrench.")
            .define("showTransferVisuals", true);

    public static final ModConfigSpec.IntValue maxTransferVisualsSpec = builder
            .comment("Maximum simultaneous resource transfer paths.")
            .defineInRange("maxTransferVisuals", 100, 1, 1000);

    private static final List<String> THEMES = List.of(
            "light", "dark", "redstone", "nebula", "glass", "terminal", "pastel", "brutalist");

    public static final ModConfigSpec.ConfigValue<String> themeSpec = builder
            .comment("GUI theme for logistics node screens: light, dark, redstone, nebula, glass, terminal, pastel, brutalist")
            .define("theme", "dark", o -> o instanceof String s && THEMES.contains(s));

    public static final ModConfigSpec.BooleanValue computerClassicThemeSpec = builder
            .comment("Use the classic green terminal look for the network computer instead of the selected GUI theme.")
            .define("computerClassicTheme", true);

    static final ModConfigSpec SPEC = builder.build();

    public static boolean defaultNodeVisibility = true;
    public static int maxRenderedNodes = 200;
    public static int maxVisibleNodes = 500;
    public static boolean connectedNodeTextures = true;
    public static boolean showTransferVisuals = true;
    public static int maxTransferVisuals = 100;
    public static boolean computerClassicTheme = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        refresh();
    }

    public static void refresh() {
        defaultNodeVisibility = defaultNodeVisibilitySpec.get();
        maxRenderedNodes = maxRenderedNodesSpec.get();
        maxVisibleNodes = maxVisibleNodesSpec.get();
        connectedNodeTextures = connectedNodeTexturesSpec.get();
        showTransferVisuals = showTransferVisualsSpec.get();
        maxTransferVisuals = maxTransferVisualsSpec.get();
        computerClassicTheme = computerClassicThemeSpec.get();
        ThemeState.applyFromConfig(themeSpec.get());
    }
}
