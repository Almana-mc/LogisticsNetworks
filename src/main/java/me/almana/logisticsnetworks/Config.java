package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.data.ChannelType;
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

    private static final ForgeConfigSpec.IntValue backoffMaxTicksSpec;
    private static final ForgeConfigSpec.BooleanValue backoffItemSpec;
    private static final ForgeConfigSpec.BooleanValue backoffFluidSpec;
    private static final ForgeConfigSpec.BooleanValue backoffEnergySpec;
    private static final ForgeConfigSpec.BooleanValue backoffChemicalSpec;
    private static final ForgeConfigSpec.BooleanValue backoffSourceSpec;

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

    static final ForgeConfigSpec SPEC = builder.build();

    public static boolean dropNodeItem;
    public static boolean debugMode;
    public static int backoffMaxTicks = 40;
    public static boolean[] backoffEnabled = {true, true, true, true, true};

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
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

