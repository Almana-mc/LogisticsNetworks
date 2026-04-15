package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.data.ChannelType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue dropNodeItemSpec = builder
            .comment("Whether nodes should drop their item when the attached block is broken.")
            .define("dropNodeItem", true);

    public static final ModConfigSpec.BooleanValue debugModeSpec = builder
            .comment("Enable debug overlays and diagnostic logging.")
            .define("debugMode", false);

    public static final ModConfigSpec.IntValue backoffMaxTicksSpec;
    public static final ModConfigSpec.BooleanValue backoffItemSpec;
    public static final ModConfigSpec.BooleanValue backoffFluidSpec;
    public static final ModConfigSpec.BooleanValue backoffEnergySpec;
    public static final ModConfigSpec.BooleanValue backoffChemicalSpec;
    public static final ModConfigSpec.BooleanValue backoffSourceSpec;

    static {
        builder.push("backoff");
        backoffMaxTicksSpec = builder
                .comment("Maximum backoff ticks for item, fluid, chemical, and source transfers.")
                .defineInRange("backoffMaxTicks", 40, 1, 200);
        backoffItemSpec = builder.comment("Enable backoff for item transfers").define("backoffItem", true);
        backoffFluidSpec = builder.comment("Enable backoff for fluid transfers").define("backoffFluid", true);
        backoffEnergySpec = builder.comment("Enable backoff for energy transfers").define("backoffEnergy", true);
        backoffChemicalSpec = builder.comment("Enable backoff for chemical transfers").define("backoffChemical", true);
        backoffSourceSpec = builder.comment("Enable backoff for source transfers").define("backoffSource", true);
        builder.pop();
    }

    static final ModConfigSpec SPEC = builder.build();

    public static boolean dropNodeItem;
    public static boolean debugMode;
    public static int backoffMaxTicks = 40;
    public static boolean[] backoffEnabled = {true, true, true, true, true};

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        refresh();
    }

    public static void refresh() {
        dropNodeItem = dropNodeItemSpec.get();
        debugMode = debugModeSpec.get();
        backoffMaxTicks = backoffMaxTicksSpec.get();
        backoffEnabled[ChannelType.ITEM.ordinal()] = backoffItemSpec.get();
        backoffEnabled[ChannelType.FLUID.ordinal()] = backoffFluidSpec.get();
        backoffEnabled[ChannelType.ENERGY.ordinal()] = backoffEnergySpec.get();
        backoffEnabled[ChannelType.CHEMICAL.ordinal()] = backoffChemicalSpec.get();
        backoffEnabled[ChannelType.SOURCE.ordinal()] = backoffSourceSpec.get();
    }
}
