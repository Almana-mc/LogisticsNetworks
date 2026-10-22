package me.almana.logisticsnetworks.client.lnet;

import me.almana.logisticsnetworks.component.FilterComponentData;
import me.almana.logisticsnetworks.component.FilterSettings;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.GeneralFilterConfig;
import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.ModFilterConfig;
import me.almana.logisticsnetworks.component.NameFilterConfig;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.ModFilterData;
import me.almana.logisticsnetworks.filter.NameFilterData;
import me.almana.logisticsnetworks.filter.NameMatchScope;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

final class LnetFilterText {

    private static final Map<NameMatchScope, String> SCOPE_WORDS = Map.of(
            NameMatchScope.NAME, "names",
            NameMatchScope.TOOLTIP, "tooltips",
            NameMatchScope.BOTH, "names or tooltips");

    static final class FilterDraft {
        final ItemStack stack;
        @Nullable
        final List<GeneralFilterEntry> entries;
        final FilterSettings settings;
        boolean droppedCard;
        final EnumSet<ChannelType> skippedKinds = EnumSet.noneOf(ChannelType.class);

        private FilterDraft(ItemStack stack, @Nullable List<GeneralFilterEntry> entries, FilterSettings settings) {
            this.stack = stack;
            this.entries = entries;
            this.settings = settings;
        }

        static FilterDraft general(FilterSettings settings) {
            ItemStack stack = new ItemStack(Registration.SMALL_FILTER.get());
            FilterSettingsData.set(stack, settings);
            return new FilterDraft(stack, new ArrayList<>(), settings);
        }

        void addEntry(String text, @Nullable ChannelMode mode, HolderLookup.Provider registries,
                Consumer<String> skip) throws IOException {
            try {
                entries.add(LnetEntryText.readEntry(text, entries.size(), mode, settings.target(), registries));
            } catch (LnetEntryText.MissingMod e) {
                skip.accept(e.getMessage());
                skippedKinds.add(e.kind);
            }
            checkSize();
        }

        void addCard(String text, HolderLookup.Provider registries, Consumer<String> skip) throws IOException {
            try {
                entries.add(LnetRecipeText.cardEntry(LnetRecipeText.readCard(text, registries), entries.size()));
            } catch (LnetEntryText.MissingMod e) {
                skip.accept("recipe dropped, " + e.getMessage());
                droppedCard = true;
                skippedKinds.addAll(EnumSet.allOf(ChannelType.class));
            }
            checkSize();
        }

        boolean hasCard() {
            return entries != null && entries.stream().anyMatch(LnetRecipeText::isCard);
        }

        ItemStack build() {
            if (entries != null && !entries.isEmpty()) {
                stack.set(LogisticsDataComponents.FILTER_ENTRIES.get(), new GeneralFilterConfig(entries));
            }
            return stack;
        }

        private void checkSize() throws IOException {
            int capacity = FilterItemData.getCapacity(stack);
            if (entries.size() > capacity) {
                throw new IOException("more than " + capacity + " entries in one filter");
            }
        }
    }

    private LnetFilterText() {
    }

    static FilterDraft readFilter(String line, @Nullable ChannelMode mode, HolderLookup.Provider registries,
            Consumer<String> skip) throws IOException {
        int colon = line.indexOf(':');
        if (colon < 0) {
            throw new IOException("filter line needs ':'");
        }
        List<String> words = new ArrayList<>(List.of(line.substring(0, colon).trim().split("\\s+")));
        String body = line.substring(colon + 1).trim();
        String first = words.remove(0);
        if (!first.equals("allow") && !first.equals("block")) {
            throw new IOException("filter line must start with 'allow' or 'block'");
        }
        boolean blacklist = first.equals("block");
        FilterTargetType target = FilterTargetType.ITEMS;
        if (!words.isEmpty() && (words.get(0).equals("fluid") || words.get(0).equals("chemical"))) {
            target = words.remove(0).equals("fluid") ? FilterTargetType.FLUIDS : FilterTargetType.CHEMICALS;
        }
        FilterSettings settings = new FilterSettings(target, blacklist);
        String kind = String.join(" ", words);
        return switch (kind) {
            case "" -> generalDraft(settings, body, mode, registries, skip);
            case "mods" -> modFilter(settings, body);
            case "names" -> nameFilter(settings, body, NameMatchScope.NAME);
            case "tooltips" -> nameFilter(settings, body, NameMatchScope.TOOLTIP);
            case "names or tooltips" -> nameFilter(settings, body, NameMatchScope.BOTH);
            default -> throw new IOException("unknown filter kind '" + kind + "'");
        };
    }

    static FilterDraft readRaw(String body, HolderLookup.Provider registries) throws IOException {
        ItemStack stack = LnetEntryText.readItem(body, registries);
        return new FilterDraft(stack, null, FilterSettingsData.get(stack));
    }

    private static FilterDraft generalDraft(FilterSettings settings, String body, @Nullable ChannelMode mode,
            HolderLookup.Provider registries, Consumer<String> skip) throws IOException {
        FilterDraft draft = FilterDraft.general(settings);
        if (!body.isEmpty()) {
            for (String text : LnetEntryText.split(body, ",")) {
                draft.addEntry(text, mode, registries, skip);
            }
        }
        return draft;
    }

    private static FilterDraft modFilter(FilterSettings settings, String body) throws IOException {
        List<String> namespaces = new ArrayList<>();
        if (!body.isEmpty()) {
            for (String namespace : body.split(",")) {
                String clean = ModFilterData.normalizeModId(namespace);
                if (clean == null) {
                    throw new IOException("bad mod id '" + namespace.trim() + "'");
                }
                namespaces.add(clean);
            }
        }
        ItemStack stack = new ItemStack(Registration.MOD_FILTER.get());
        FilterSettingsData.set(stack, settings);
        if (!namespaces.isEmpty()) {
            stack.set(LogisticsDataComponents.MOD_FILTER.get(), new ModFilterConfig(namespaces));
        }
        return new FilterDraft(stack, null, settings);
    }

    private static FilterDraft nameFilter(FilterSettings settings, String body, NameMatchScope scope) {
        ItemStack stack = new ItemStack(Registration.NAME_FILTER.get());
        FilterSettingsData.set(stack, settings);
        if (!body.isEmpty() || scope != NameMatchScope.NAME) {
            stack.set(LogisticsDataComponents.NAME_FILTER.get(), new NameFilterConfig(body, scope));
        }
        return new FilterDraft(stack, null, settings);
    }

    static List<String> write(ItemStack filter, ChannelMode mode, HolderLookup.Provider registries) {
        FilterComponentData.migrate(filter, registries);
        FilterSettings settings = FilterSettingsData.get(filter);
        String head = (settings.blacklist() ? "block" : "allow") + targetWord(settings.target());
        if (isGeneral(filter)) {
            return writeGeneral(filter, head, settings, mode, registries);
        }
        if (ModFilterData.isModFilter(filter) && onlyHas(filter, LogisticsDataComponents.MOD_FILTER.get())) {
            String mods = String.join(", ", ModFilterData.getModFilters(filter));
            return List.of(withBody(head + " mods:", mods));
        }
        if (NameFilterData.isNameFilter(filter) && onlyHas(filter, LogisticsDataComponents.NAME_FILTER.get())) {
            NameFilterConfig config = filter.getOrDefault(LogisticsDataComponents.NAME_FILTER.get(),
                    new NameFilterConfig("", NameMatchScope.NAME));
            String expression = LnetEntryText.oneLine(config.expression());
            return List.of(withBody(head + " " + SCOPE_WORDS.get(config.scope()) + ":", expression));
        }
        return List.of("raw filter: " + LnetEntryText.writeItem(filter, registries));
    }

    private static List<String> writeGeneral(ItemStack filter, String head, FilterSettings settings,
            ChannelMode mode, HolderLookup.Provider registries) {
        List<String> lines = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        for (GeneralFilterEntry entry : entries(filter)) {
            if (LnetRecipeText.isCard(entry)) {
                RecipeCardData card = RecipeCardData.of(entry.item().toStack());
                if (!card.ingredients().isEmpty()) {
                    lines.add("recipe: " + LnetRecipeText.writeCard(card, registries));
                }
            } else if (!LnetEntryText.isBlank(entry)) {
                plain.add(LnetEntryText.writeEntry(entry, settings.target(), mode, registries));
            }
        }
        lines.add(0, withBody(head + ":", String.join(", ", plain)));
        return lines;
    }

    private static String withBody(String head, String body) {
        return body.isEmpty() ? head : head + " " + body;
    }

    static List<GeneralFilterEntry> inlineable(ItemStack filter, HolderLookup.Provider registries) {
        FilterComponentData.migrate(filter, registries);
        if (!isGeneral(filter) || !FilterSettingsData.get(filter).isDefault()) {
            return List.of();
        }
        List<GeneralFilterEntry> entries = entries(filter).stream().filter(entry -> !LnetEntryText.isBlank(entry))
                .toList();
        boolean simple = entries.stream().allMatch(entry -> LnetEntryText.hasTarget(entry)
                && !LnetRecipeText.isCard(entry)
                && entry.counts().stock() == 0 && entry.counts().amount() == 0
                && entry.slotMapping().slots().isEmpty()
                && entry.nbt().equals(GeneralFilterEntry.NbtConstraints.EMPTY));
        return simple ? entries : List.of();
    }

    private static boolean isGeneral(ItemStack stack) {
        return FilterItemData.isFilterItem(stack) && onlyHas(stack, LogisticsDataComponents.FILTER_ENTRIES.get());
    }

    private static boolean onlyHas(ItemStack stack, DataComponentType<?> extra) {
        Set<DataComponentType<?>> known = new HashSet<>(List.of(extra, LogisticsDataComponents.FILTER_SETTINGS.get()));
        for (Map.Entry<DataComponentType<?>, Optional<?>> change : stack.getComponentsPatch().entrySet()) {
            if (change.getValue().isEmpty() || !known.contains(change.getKey())) {
                return false;
            }
        }
        return true;
    }

    private static List<GeneralFilterEntry> entries(ItemStack stack) {
        GeneralFilterConfig config = stack.get(LogisticsDataComponents.FILTER_ENTRIES.get());
        return config == null ? List.of() : config.entries().stream()
                .sorted(Comparator.comparingInt(GeneralFilterEntry::slot))
                .toList();
    }

    private static String targetWord(FilterTargetType target) {
        return switch (target) {
            case FLUIDS -> " fluid";
            case CHEMICALS -> " chemical";
            case ITEMS -> "";
        };
    }
}
