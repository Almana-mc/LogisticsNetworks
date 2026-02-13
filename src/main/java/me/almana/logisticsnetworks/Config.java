package me.almana.logisticsnetworks;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue dropNodeItemSpec = builder
            .comment("Whether nodes should drop their item when the attached block is broken.")
            .define("dropNodeItem", true);

    private static final ModConfigSpec.BooleanValue debugModeSpec = builder
            .comment("Enable debug overlays and diagnostic logging.")
            .define("debugMode", false);

    static final ModConfigSpec SPEC = builder.build();

    public static boolean dropNodeItem;
    public static boolean debugMode;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        dropNodeItem = dropNodeItemSpec.get();
        debugMode = debugModeSpec.get();
    }
}
