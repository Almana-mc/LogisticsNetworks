package me.almana.logisticsnetworks.client.lnet;

import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.ModFilterData;
import me.almana.logisticsnetworks.filter.NameFilterData;
import me.almana.logisticsnetworks.filter.NameMatchScope;
import me.almana.logisticsnetworks.registration.Registration;
import me.almana.logisticsnetworks.util.ItemDataUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class LnetFilterText {

    private static final Map<NameMatchScope, String> SCOPE_WORDS = Map.of(
            NameMatchScope.NAME, "names",
            NameMatchScope.TOOLTIP, "tooltips",
            NameMatchScope.BOTH, "names or tooltips");

    record FilterSettings(FilterTargetType target, boolean blacklist) {
        static final FilterSettings DEFAULT = new FilterSettings(FilterTargetType.ITEMS, false);

        boolean isDefault() {
            return target == FilterTargetType.ITEMS && !blacklist;
        }
    }

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
            FilterItemData.setTargetType(stack, settings.target());
            FilterItemData.setBlacklist(stack, settings.blacklist());
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

        // 32 KB filter cap
        ItemStack build() throws IOException {
            if (entries != null && !entries.isEmpty() && !FilterItemData.saveEntries(stack, entries)) {
                throw new IOException("filter is over 32 KB");
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
        return new FilterDraft(stack, null, settings(stack));
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
        ModFilterData.setTargetType(stack, settings.target());
        ModFilterData.setBlacklist(stack, settings.blacklist());
        for (String namespace : namespaces) {
            ModFilterData.addModFilter(stack, namespace);
        }
        return new FilterDraft(stack, null, settings);
    }

    private static FilterDraft nameFilter(FilterSettings settings, String body, NameMatchScope scope) {
        ItemStack stack = new ItemStack(Registration.NAME_FILTER.get());
        NameFilterData.setTargetType(stack, settings.target());
        NameFilterData.setBlacklist(stack, settings.blacklist());
        NameFilterData.setNameFilter(stack, body);
        NameFilterData.setMatchScope(stack, scope);
        return new FilterDraft(stack, null, settings);
    }

    static List<String> write(ItemStack filter, ChannelMode mode, HolderLookup.Provider registries) {
        FilterSettings settings = settings(filter);
        String head = (settings.blacklist() ? "block" : "allow") + targetWord(settings.target());
        if (isGeneral(filter)) {
            return writeGeneral(filter, head, settings, mode, registries);
        }
        if (ModFilterData.isModFilter(filter) && onlyHas(filter, "ln_mod_filter")) {
            String mods = String.join(", ", ModFilterData.getModFilters(filter));
            return List.of(withBody(head + " mods:", mods));
        }
        if (NameFilterData.isNameFilter(filter) && onlyHas(filter, "ln_name_filter")) {
            String expression = LnetEntryText.oneLine(NameFilterData.getNameFilter(filter));
            String scope = SCOPE_WORDS.get(NameFilterData.getMatchScope(filter));
            return List.of(withBody(head + " " + scope + ":", expression));
        }
        return List.of("raw filter: " + LnetEntryText.writeItem(filter, registries));
    }

    private static List<String> writeGeneral(ItemStack filter, String head, FilterSettings settings,
            ChannelMode mode, HolderLookup.Provider registries) {
        if (!entries(filter).stream().allMatch(entry -> survives(entry, settings.target(), mode, registries))) {
            return List.of("raw filter: " + LnetEntryText.writeItem(filter, registries));
        }
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
        if (!joinable(plain)) {
            return List.of("raw filter: " + LnetEntryText.writeItem(filter, registries));
        }
        lines.add(0, withBody(head + ":", String.join(", ", plain)));
        return lines;
    }

    private static boolean joinable(List<String> parts) {
        return parts.isEmpty() || LnetEntryText.split(String.join(", ", parts), ",").size() == parts.size();
    }

    private static boolean survives(GeneralFilterEntry entry, FilterTargetType target, ChannelMode mode,
            HolderLookup.Provider registries) {
        try {
            if (LnetRecipeText.isCard(entry)) {
                RecipeCardData card = RecipeCardData.of(entry.item().toStack());
                if (!card.ingredients().isEmpty()) {
                    LnetRecipeText.readCard(LnetRecipeText.writeCard(card, registries), registries);
                }
            } else if (!LnetEntryText.isBlank(entry)) {
                String text = LnetEntryText.writeEntry(entry, target, mode, registries);
                LnetEntryText.readEntry(text, 0, mode, target, registries);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static String withBody(String head, String body) {
        return body.isEmpty() ? head : head + " " + body;
    }

    static List<GeneralFilterEntry> inlineable(ItemStack filter, HolderLookup.Provider registries) {
        if (!isGeneral(filter) || !settings(filter).isDefault()) {
            return List.of();
        }
        List<GeneralFilterEntry> entries = entries(filter).stream().filter(entry -> !LnetEntryText.isBlank(entry))
                .toList();
        boolean simple = entries.stream().allMatch(entry -> LnetEntryText.hasTarget(entry)
                && !LnetRecipeText.isCard(entry)
                && entry.counts().stock() == 0 && entry.counts().amount() == 0
                && entry.slotMapping().slots().isEmpty()
                && entry.nbt().equals(GeneralFilterEntry.NbtConstraints.EMPTY));
        boolean readable = entries.stream()
                .allMatch(entry -> survives(entry, FilterTargetType.ITEMS, ChannelMode.EXPORT, registries));
        List<String> texts = entries.stream()
                .map(entry -> LnetEntryText.writeEntry(entry, FilterTargetType.ITEMS, ChannelMode.EXPORT, registries))
                .toList();
        return simple && readable && joinable(texts) ? entries : List.of();
    }

    private static boolean isGeneral(ItemStack stack) {
        return FilterItemData.isFilterItem(stack) && onlyHas(stack, "ln_filter");
    }

    // Tag holds only this root
    private static boolean onlyHas(ItemStack stack, String root) {
        CompoundTag tag = stack.getTag();
        Set<String> custom = ItemDataUtil.peekCustomData(stack).getAllKeys();
        return (tag == null || tag.size() == (custom.isEmpty() ? 0 : 1)) && Set.of(root).containsAll(custom);
    }

    private static FilterSettings settings(ItemStack stack) {
        if (ModFilterData.isModFilter(stack)) {
            return new FilterSettings(ModFilterData.getTargetType(stack), ModFilterData.isBlacklist(stack));
        }
        if (NameFilterData.isNameFilter(stack)) {
            return new FilterSettings(NameFilterData.getTargetType(stack), NameFilterData.isBlacklist(stack));
        }
        return new FilterSettings(FilterItemData.getTargetType(stack), FilterItemData.isBlacklist(stack));
    }

    private static List<GeneralFilterEntry> entries(ItemStack stack) {
        return FilterItemData.getEntries(stack).stream()
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
