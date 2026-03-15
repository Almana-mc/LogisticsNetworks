package me.almana.logisticsnetworks;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.ForgeConfigSpec;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue dropNodeItemSpec = builder
            .comment("Whether nodes should drop their item when the attached block is broken.")
            .define("dropNodeItem", true);

    private static final ForgeConfigSpec.BooleanValue debugModeSpec = builder
            .comment("Enable debug overlays and diagnostic logging.")
            .define("debugMode", false);

    static final ForgeConfigSpec SPEC = builder.build();

    public static boolean dropNodeItem;
    public static boolean debugMode;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        dropNodeItem = dropNodeItemSpec.get();
        debugMode = debugModeSpec.get();
    }
}

