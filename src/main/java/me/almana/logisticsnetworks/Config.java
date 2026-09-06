package me.almana.logisticsnetworks;

import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.logic.async.AsyncTransferRuntime;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Set;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID)
public class Config {

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue dropNodeItemSpec = builder
            .comment("Whether nodes should drop their item when the attached block is broken.")
            .define("dropNodeItem", true);

    public static final ModConfigSpec.BooleanValue debugModeSpec = builder
            .comment("Enable debug overlays and diagnostic logging.")
            .define("debugMode", false);

    private static final Set<String> NODE_ACCESS_MODES = Set.of("Teams", "All", "Allies");

    public static final ModConfigSpec.ConfigValue<String> nodeAccessModeSpec = builder
            .comment("Controls who can access nodes and networks. Allowed values: Teams, All, Allies.")
            .define("nodeAccessMode", "Teams", value -> value instanceof String name && NODE_ACCESS_MODES.contains(name));

    public static final ModConfigSpec.BooleanValue juneAwarenessMessageSpec = builder
            .comment("Send June awareness message.")
            .define("juneAwarenessMessage", true);

    public static final ModConfigSpec.BooleanValue networkTickingEnabledSpec = builder
            .comment("Whether logistics networks are processed each server tick. "
                    + "Disable to isolate network-related crashes for debugging.")
            .define("networkTickingEnabled", true);

    public static final ModConfigSpec.IntValue backoffMaxTicksSpec;
    public static final ModConfigSpec.BooleanValue backoffItemSpec;
    public static final ModConfigSpec.BooleanValue backoffFluidSpec;
    public static final ModConfigSpec.BooleanValue backoffEnergySpec;
    public static final ModConfigSpec.BooleanValue backoffChemicalSpec;
    public static final ModConfigSpec.BooleanValue backoffSourceSpec;

    public static final ModConfigSpec.BooleanValue asyncPlanningSpec;
    public static final ModConfigSpec.IntValue asyncWorkerThreadsSpec;
    public static final ModConfigSpec.IntValue asyncCommitBudgetUsSpec;
    public static final ModConfigSpec.IntValue asyncMaxOccupiedSlotsSpec;

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

        builder.push("async");
        asyncPlanningSpec = builder
                .comment("Plan item transfers on worker threads. Disable to force the synchronous path.")
                .define("asyncPlanning", true);
        asyncWorkerThreadsSpec = builder
                .comment("Worker threads for transfer planning. 0 = auto (min(4, cores - 2)).")
                .defineInRange("asyncWorkerThreads", 0, 0, 16);
        asyncCommitBudgetUsSpec = builder
                .comment("Microseconds per tick spent committing plans. At least one plan always commits.")
                .defineInRange("asyncCommitBudgetUs", 2000, 100, 50000);
        asyncMaxOccupiedSlotsSpec = builder
                .comment("Safety valve. A network with more occupied slots than this falls back to synchronous.")
                .defineInRange("asyncMaxOccupiedSlots", 200000, 1000, 5000000);
        builder.pop();
    }

    public static final ModConfigSpec SPEC = builder.build();

    public static boolean dropNodeItem;
    public static boolean debugMode;
    public static NodeAccessMode nodeAccessMode = NodeAccessMode.TEAMS;
    public static boolean juneAwarenessMessage;
    public static boolean networkTickingEnabled;
    public static int backoffMaxTicks = 40;
    public static boolean asyncPlanning = true;
    public static int asyncWorkerThreads = 0;
    public static int asyncCommitBudgetUs = 2000;
    public static int asyncMaxOccupiedSlots = 200000;
    public static boolean[] backoffEnabled = {true, true, true, true, true};

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        refresh();
    }

    public static void refresh() {
        dropNodeItem = dropNodeItemSpec.get();
        debugMode = debugModeSpec.get();
        nodeAccessMode = NodeAccessMode.fromSerializedName(nodeAccessModeSpec.get());
        juneAwarenessMessage = juneAwarenessMessageSpec.get();
        networkTickingEnabled = networkTickingEnabledSpec.get();
        backoffMaxTicks = backoffMaxTicksSpec.get();
        asyncPlanning = asyncPlanningSpec.get();
        asyncWorkerThreads = asyncWorkerThreadsSpec.get();
        asyncCommitBudgetUs = asyncCommitBudgetUsSpec.get();
        asyncMaxOccupiedSlots = asyncMaxOccupiedSlotsSpec.get();
        backoffEnabled[ChannelType.ITEM.ordinal()] = backoffItemSpec.get();
        backoffEnabled[ChannelType.FLUID.ordinal()] = backoffFluidSpec.get();
        backoffEnabled[ChannelType.ENERGY.ordinal()] = backoffEnergySpec.get();
        backoffEnabled[ChannelType.CHEMICAL.ordinal()] = backoffChemicalSpec.get();
        backoffEnabled[ChannelType.SOURCE.ordinal()] = backoffSourceSpec.get();
        AsyncTransferRuntime.requestReload();
    }
}
