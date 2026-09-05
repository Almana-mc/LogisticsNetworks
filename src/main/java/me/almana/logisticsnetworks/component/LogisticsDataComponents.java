package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LogisticsDataComponents {

    public static final DeferredRegister.DataComponents REGISTRAR = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, LogisticsNetworks.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterSettings>> FILTER_SETTINGS =
            REGISTRAR.registerComponentType("filter_settings", builder -> builder.persistent(FilterSettings.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GeneralFilterConfig>> FILTER_ENTRIES =
            REGISTRAR.registerComponentType("filter_entries", builder -> builder.persistent(GeneralFilterConfig.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TagFilterConfig>> TAG_FILTER =
            REGISTRAR.registerComponentType("tag_filter", builder -> builder.persistent(TagFilterConfig.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ModFilterConfig>> MOD_FILTER =
            REGISTRAR.registerComponentType("mod_filter", builder -> builder.persistent(ModFilterConfig.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NameFilterConfig>> NAME_FILTER =
            REGISTRAR.registerComponentType("name_filter", builder -> builder.persistent(NameFilterConfig.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AmountFilterConfig>> AMOUNT_FILTER =
            REGISTRAR.registerComponentType("amount_filter", builder -> builder.persistent(AmountFilterConfig.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DurabilityFilterConfig>> DURABILITY_FILTER =
            REGISTRAR.registerComponentType("durability_filter", builder -> builder.persistent(DurabilityFilterConfig.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NbtFilterConfig>> NBT_FILTER =
            REGISTRAR.registerComponentType("nbt_filter", builder -> builder.persistent(NbtFilterConfig.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SlotFilterConfig>> SLOT_FILTER =
            REGISTRAR.registerComponentType("slot_filter", builder -> builder.persistent(SlotFilterConfig.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WrenchItem.Mode>> WRENCH_MODE =
            REGISTRAR.registerComponentType("wrench_mode", builder -> builder.persistent(WrenchItem.Mode.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WrenchClipboard>> WRENCH_CLIPBOARD =
            REGISTRAR.registerComponentType("wrench_clipboard", builder -> builder.persistent(WrenchClipboard.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> WRENCH_AE2_LINK =
            REGISTRAR.registerComponentType("wrench_ae2_link", builder -> builder.persistent(GlobalPos.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WrenchMassPlacement>> WRENCH_MASS_PLACEMENT =
            REGISTRAR.registerComponentType("wrench_mass_placement",
                    builder -> builder.persistent(WrenchMassPlacement.CODEC));

    private LogisticsDataComponents() {
    }
}
