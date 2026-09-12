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

    public static final ModConfigSpec.BooleanValue invertCopyPasteControlsSpec = builder
            .comment("Invert the wrench copy/paste controls so right-click pastes and modifier 1 + right-click copies.")
            .define("invertCopyPasteControls", false);

    public static final ModConfigSpec.IntValue maxRenderedNodesSpec = builder
            .comment("Maximum number of nodes rendered when holding a wrench. Nearest nodes are prioritized.")
            .defineInRange("maxRenderedNodes", 200, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue maxVisibleNodesSpec = builder
            .comment("Maximum number of visible node models rendered. Nearest nodes are prioritized. 0 = unlimited.")
            .defineInRange("maxVisibleNodes", 500, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue connectedNodeTexturesSpec = builder
            .comment("Whether adjacent visible nodes should render as connected.")
            .define("connectedNodeTextures", true);

    public static final ModConfigSpec.BooleanValue flowLinesEnabledSpec = builder
            .comment("Show wrench flow connections.")
            .define("flowLinesEnabled", true);

    public static final ModConfigSpec.DoubleValue flowLineThicknessSpec = builder
            .comment("Line width in pixels.")
            .defineInRange("flowLineThickness", 6.0, 1.0, 20.0);

    public static final ModConfigSpec.DoubleValue flowLineSpeedSpec = builder
            .comment("Reveal and pulse blocks/second.")
            .defineInRange("flowLineSpeed", 3.0, 0.1, 60.0);

    public static final ModConfigSpec.DoubleValue flowLineOpacitySpec = builder
            .comment("Line opacity, including pulses.")
            .defineInRange("flowLineOpacity", 0.95, 0.05, 1.0);

    public static final ModConfigSpec.BooleanValue flowLinePulsesSpec = builder
            .comment("Show moving resource pulses.")
            .define("flowLinePulses", true);

    public static final ModConfigSpec.DoubleValue flowLinePulseSpacingSpec = builder
            .comment("Pulse spacing in blocks.")
            .defineInRange("flowLinePulseSpacing", 3.0, 0.5, 32.0);

    public static final ModConfigSpec.DoubleValue flowLinePulseLengthSpec = builder
            .comment("Pulse length in blocks.")
            .defineInRange("flowLinePulseLength", 0.6, 0.1, 8.0);

    public static final ModConfigSpec.BooleanValue flowLinesThroughBlocksSpec = builder
            .comment("Show connections through blocks.")
            .define("flowLinesThroughBlocks", true);

    private static final List<String> THEMES = List.of(
            "light", "dark", "redstone", "nebula", "glass", "terminal", "pastel", "brutalist");

    public static final ModConfigSpec.ConfigValue<String> themeSpec = builder
            .comment("GUI theme for logistics node screens: light, dark, redstone, nebula, glass, terminal, pastel, brutalist")
            .define("theme", "dark", o -> o instanceof String s && THEMES.contains(s));

    static final ModConfigSpec SPEC = builder.build();

    public static boolean defaultNodeVisibility = true;
    public static boolean invertCopyPasteControls = false;
    public static int maxRenderedNodes = 200;
    public static int maxVisibleNodes = 500;
    public static boolean connectedNodeTextures = true;
    public static boolean flowLinesEnabled = true;
    public static double flowLineThickness = 6;
    public static double flowLineSpeed = 3;
    public static double flowLineOpacity = 0.95;
    public static boolean flowLinePulses = true;
    public static double flowLinePulseSpacing = 3;
    public static double flowLinePulseLength = 0.6;
    public static boolean flowLinesThroughBlocks = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        refresh();
    }

    public static void refresh() {
        defaultNodeVisibility = defaultNodeVisibilitySpec.get();
        invertCopyPasteControls = invertCopyPasteControlsSpec.get();
        maxRenderedNodes = maxRenderedNodesSpec.get();
        maxVisibleNodes = maxVisibleNodesSpec.get();
        connectedNodeTextures = connectedNodeTexturesSpec.get();
        flowLinesEnabled = flowLinesEnabledSpec.get();
        flowLineThickness = flowLineThicknessSpec.get();
        flowLineSpeed = flowLineSpeedSpec.get();
        flowLineOpacity = flowLineOpacitySpec.get();
        flowLinePulses = flowLinePulsesSpec.get();
        flowLinePulseSpacing = flowLinePulseSpacingSpec.get();
        flowLinePulseLength = flowLinePulseLengthSpec.get();
        flowLinesThroughBlocks = flowLinesThroughBlocksSpec.get();
        ThemeState.applyFromConfig(themeSpec.get());
    }
}
