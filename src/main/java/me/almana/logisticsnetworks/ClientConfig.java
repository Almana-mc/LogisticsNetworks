package me.almana.logisticsnetworks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public class ClientConfig {

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue maxRenderedNodesSpec = builder
            .comment("Maximum number of nodes rendered when holding a wrench. Nearest nodes are prioritized.")
            .defineInRange("maxRenderedNodes", 200, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue maxVisibleNodesSpec = builder
            .comment("Maximum number of visible node models rendered. Nearest nodes are prioritized. 0 = unlimited.")
            .defineInRange("maxVisibleNodes", 500, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = builder.build();

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
    }
}
