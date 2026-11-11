package me.almana.logisticsnetworks.client.tooltip;

import me.almana.logisticsnetworks.component.FilterComponentData;
import me.almana.logisticsnetworks.component.GeneralFilterConfig;
import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public record FilterPreviewTooltip(List<Entry> entries) implements TooltipComponent {

    public FilterPreviewTooltip {
        entries = List.copyOf(entries);
    }

    public static FilterPreviewTooltip from(ItemStack filter, HolderLookup.Provider provider) {
        FilterComponentData.migrate(filter, provider);
        GeneralFilterConfig config = filter.getOrDefault(LogisticsDataComponents.FILTER_ENTRIES,
                new GeneralFilterConfig(List.of()));
        return fromEntries(config.entries());
    }

    static FilterPreviewTooltip fromEntries(List<GeneralFilterEntry> entries) {
        return new FilterPreviewTooltip(entries.stream()
                .sorted(Comparator.comparingInt(GeneralFilterEntry::slot))
                .map(FilterPreviewTooltip::previewEntry)
                .toList());
    }

    private static Entry previewEntry(GeneralFilterEntry entry) {
        if (entry.item() != null) {
            return new Entry(Kind.ITEM, entry.item().toStack().copyWithCount(1), "");
        }
        if (entry.fluidId() != null) {
            return new Entry(Kind.FLUID, ItemStack.EMPTY, entry.fluidId());
        }
        if (entry.chemicalId() != null) {
            return new Entry(Kind.CHEMICAL, ItemStack.EMPTY, entry.chemicalId());
        }
        if (entry.tag() != null) {
            return new Entry(Kind.TAG, ItemStack.EMPTY, entry.tag());
        }
        return new Entry(Kind.RULE, ItemStack.EMPTY, "");
    }

    public enum Kind {
        ITEM,
        FLUID,
        CHEMICAL,
        TAG,
        RULE
    }

    public record Entry(Kind kind, ItemStack item, String id) {
    }
}
