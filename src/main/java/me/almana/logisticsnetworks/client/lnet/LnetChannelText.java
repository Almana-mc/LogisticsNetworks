package me.almana.logisticsnetworks.client.lnet;

import me.almana.logisticsnetworks.component.ClipboardSnapshot.ChannelState;
import me.almana.logisticsnetworks.component.FilterSettings;
import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.DistributionMode;
import me.almana.logisticsnetworks.data.FilterMode;
import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.data.RedstoneMode;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LnetChannelText {

    private static final Pattern RESOURCE = Pattern.compile("(max|\\d+)(mb)? (items?|fluids|energy|chemicals|source)");
    private static final Pattern EVERY = Pattern.compile("every (\\d+) ticks?");
    private static final Pattern PRIORITY = Pattern.compile("priority (-?\\d+)");
    private static final Pattern RECIPE_SETS = Pattern.compile("(\\d+) recipe sets?");
    private static final List<String> WORDS = List.of("items", "fluids", "energy", "chemicals", "source");
    private static final int LINE_WIDTH = 100;

    static final class ChannelDraft {
        final int index;
        final String name;
        final List<LnetFilterText.FilterDraft> filters = new ArrayList<>();
        @Nullable
        ChannelMode mode;
        int recipeTarget = -1;
        int line;
        @Nullable
        private LnetFilterText.FilterDraft inline;
        private boolean enabled = true;
        private int types;
        private final int[] batches = new int[ChannelType.values().length];
        private int recipeSets;
        private int tickDelay = 20;
        private IoSide side = IoSide.UP;
        private RedstoneMode redstone = RedstoneMode.IGNORED;
        private DistributionMode distribution = DistributionMode.PRIORITY;
        private FilterMode filterMode = FilterMode.MATCH_ANY;
        private int priority;
        private boolean rotate;
        private boolean droppedFilter;

        ChannelDraft(int index, String name) {
            this.index = index;
            this.name = name;
        }

        void readClauses(String text, HolderLookup.Provider registries, Consumer<String> skip) throws IOException {
            for (String clause : LnetEntryText.split(text, ",")) {
                if (clause.isEmpty()) {
                    throw new IOException("empty clause");
                }
                readClause(clause, registries, skip);
            }
        }

        void addFilter(LnetFilterText.FilterDraft filter) throws IOException {
            checkFilterRoom();
            filters.add(filter);
        }

        ChannelState state(String label) throws IOException {
            if (mode == null) {
                throw new IOException("[" + label + "] channel " + (index + 1) + " needs a 'send' or 'receive' clause");
            }
            guardWhitelists();
            if (droppedFilter) {
                types = 0;
            }
            return new ChannelState(enabled, mode, types, Arrays.stream(batches).boxed().toList(), recipeSets,
                    tickDelay, Optional.of(side), redstone, distribution, filterMode, priority, name, rotate);
        }

        void dropFilter() {
            droppedFilter = true;
        }

        List<ItemStack> filterStacks() {
            List<ItemStack> stacks = new ArrayList<>();
            if (inline != null && !inline.entries.isEmpty()) {
                stacks.add(inline.build());
            }
            for (LnetFilterText.FilterDraft filter : filters) {
                stacks.add(filter.build());
            }
            return stacks;
        }

        private void checkFilterRoom() throws IOException {
            if (filters.size() + (inline == null ? 0 : 1) >= ChannelData.FILTER_SIZE) {
                throw new IOException("more than " + ChannelData.FILTER_SIZE + " filters on one channel");
            }
        }

        private void readClause(String clause, HolderLookup.Provider registries, Consumer<String> skip)
                throws IOException {
            if (clause.startsWith("send ") || clause.startsWith("receive ")) {
                ChannelMode verb = clause.startsWith("send ") ? ChannelMode.EXPORT : ChannelMode.IMPORT;
                if (mode != null && mode != verb) {
                    throw new IOException("channel mixes 'send' and 'receive'");
                }
                mode = verb;
                readResource(clause.substring(clause.indexOf(' ') + 1).trim(), registries, skip);
                return;
            }
            if (!readPhrase(clause)) {
                readSetting(clause);
            }
        }

        private boolean readPhrase(String clause) {
            switch (clause) {
                case "off" -> enabled = false;
                case "all sides" -> side = IoSide.ALL;
                case "no side" -> side = IoSide.NONE;
                case "nearest first" -> distribution = DistributionMode.NEAREST_FIRST;
                case "farthest first" -> distribution = DistributionMode.FARTHEST_FIRST;
                case "equal distribution" -> distribution = DistributionMode.ROUND_ROBIN;
                case "priority robin" -> distribution = DistributionMode.PRIORITY_ROBIN;
                case "when redstone high" -> redstone = RedstoneMode.HIGH;
                case "when redstone low" -> redstone = RedstoneMode.LOW;
                case "match all filters" -> filterMode = FilterMode.MATCH_ALL;
                case "match any filters" -> filterMode = FilterMode.MATCH_ANY;
                case "rotate resources" -> rotate = true;
                default -> {
                    return false;
                }
            }
            return true;
        }

        private void readSetting(String clause) throws IOException {
            Matcher every = EVERY.matcher(clause);
            Matcher priorityValue = PRIORITY.matcher(clause);
            Matcher sets = RECIPE_SETS.matcher(clause);
            if (clause.startsWith("side ")) {
                side = sideOf(clause.substring("side ".length()).trim());
            } else if (every.matches()) {
                tickDelay = LnetEntryText.number(every.group(1));
                if (tickDelay < 1) {
                    throw new IOException("'every' needs at least 1 tick");
                }
            } else if (priorityValue.matches()) {
                priority = LnetEntryText.number(priorityValue.group(1));
                if (priority < -99 || priority > 99) {
                    throw new IOException("priority must be -99 to 99");
                }
            } else if (sets.matches()) {
                recipeSets = LnetEntryText.number(sets.group(1));
            } else {
                throw new IOException("unknown clause '" + clause + "'");
            }
        }

        private void readResource(String text, HolderLookup.Provider registries, Consumer<String> skip)
                throws IOException {
            if (text.equals("everything")) {
                types = ChannelType.ALL;
                Arrays.fill(batches, 0);
                return;
            }
            if (text.equals("nothing")) {
                return;
            }
            Matcher resource = RESOURCE.matcher(text);
            if (!resource.matches()) {
                readInline(text, registries, skip);
                return;
            }
            String word = resource.group(3).equals("item") ? "items" : resource.group(3);
            ChannelType type = ChannelType.values()[WORDS.indexOf(word)];
            if (resource.group(2) != null && type != ChannelType.FLUID && type != ChannelType.CHEMICAL) {
                throw new IOException("'mb' only fits fluids and chemicals");
            }
            types |= type.bit();
            batches[type.ordinal()] = resource.group(1).equals("max") ? 0 : LnetEntryText.number(resource.group(1));
        }

        private void readInline(String text, HolderLookup.Provider registries, Consumer<String> skip)
                throws IOException {
            if (inline == null) {
                checkFilterRoom();
                inline = LnetFilterText.FilterDraft.general(FilterSettings.DEFAULT);
            }
            int before = inline.entries.size();
            inline.addEntry(text, mode, registries, skip);
            if (inline.entries.size() > before) {
                GeneralFilterEntry added = inline.entries.get(before);
                if (!LnetEntryText.hasTarget(added)) {
                    throw new IOException("'any' entries go on an allow: line");
                }
                types |= LnetEntryText.kindOf(added, FilterTargetType.ITEMS).bit();
            }
        }

        private void guardWhitelists() {
            List<LnetFilterText.FilterDraft> all = new ArrayList<>(filters);
            if (inline != null) {
                all.add(inline);
            }
            if (all.stream().anyMatch(LnetFilterText.FilterDraft::hasCard)) {
                return;
            }
            for (LnetFilterText.FilterDraft filter : all) {
                boolean guarded = filter.entries != null
                        && (!filter.settings.blacklist() || filter.droppedCard);
                if (!guarded) {
                    continue;
                }
                FilterTargetType target = filter.settings.target();
                for (ChannelType kind : filter.skippedKinds) {
                    if (filter.settings.blacklist()
                            || filter.entries.stream().noneMatch(entry -> keeps(entry, kind, target))) {
                        types &= ~kind.bit();
                    }
                }
            }
        }

        private static boolean keeps(GeneralFilterEntry entry, ChannelType kind, FilterTargetType target) {
            if (LnetEntryText.kindOf(entry, target) == kind) {
                return true;
            }
            boolean widens = kind == ChannelType.ITEM || kind == ChannelType.FLUID || kind == ChannelType.CHEMICAL;
            return widens && !LnetEntryText.hasTarget(entry) && (!entry.nbt().rules().isEmpty()
                    || !entry.nbt().raw().isBlank() || entry.counts().batch() > 0 || LnetEntryText.stockOf(entry) > 0);
        }
    }

    private LnetChannelText() {
    }

    static void write(List<String> lines, int index, ChannelState state, List<ItemStack> filters,
            HolderLookup.Provider registries) {
        List<GeneralFilterEntry> inline = inlineEntries(state, filters, registries);
        String name = state.name().isBlank() ? ""
                : " \"" + LnetEntryText.oneLine(state.name())
                        .replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        String title = "Channel " + (index + 1) + name + ": " + String.join(", ", head(state, inline, registries));
        String extra = String.join(", ", settings(state));
        if (extra.isEmpty()) {
            lines.add(title);
        } else if (title.length() + 2 + extra.length() <= LINE_WIDTH) {
            lines.add(title + ", " + extra);
        } else {
            lines.add(title);
            lines.add("  " + extra);
        }
        if (!inline.isEmpty()) {
            return;
        }
        for (ItemStack filter : filters) {
            for (String line : LnetFilterText.write(filter, state.mode(), registries)) {
                lines.add("  " + line);
            }
        }
    }

    private static List<String> head(ChannelState state, List<GeneralFilterEntry> inline,
            HolderLookup.Provider registries) {
        String verb = state.mode() == ChannelMode.EXPORT ? "send " : "receive ";
        int inlineKinds = 0;
        for (GeneralFilterEntry entry : inline) {
            inlineKinds |= LnetEntryText.kindOf(entry, FilterTargetType.ITEMS).bit();
        }
        List<String> head = new ArrayList<>();
        boolean allMax = state.batches().stream().allMatch(batch -> batch == 0);
        if (inline.isEmpty() && state.types() == ChannelType.ALL && allMax) {
            head.add(verb + "everything");
        } else if (state.types() == 0) {
            head.add(verb + "nothing");
        } else {
            for (ChannelType type : ChannelType.values()) {
                if (type.in(state.types()) && !type.in(inlineKinds)) {
                    int batch = state.batches().get(type.ordinal());
                    String word = type == ChannelType.ITEM && batch == 1 ? "item" : WORDS.get(type.ordinal());
                    head.add(verb + amount(type, batch) + " " + word);
                }
            }
        }
        for (GeneralFilterEntry entry : inline) {
            head.add(verb + LnetEntryText.writeEntry(entry, FilterTargetType.ITEMS, state.mode(), registries));
        }
        head.add(side(state.direction()));
        return head;
    }

    private static List<GeneralFilterEntry> inlineEntries(ChannelState state, List<ItemStack> filters,
            HolderLookup.Provider registries) {
        if (filters.size() != 1) {
            return List.of();
        }
        List<GeneralFilterEntry> entries = LnetFilterText.inlineable(filters.get(0), registries);
        for (GeneralFilterEntry entry : entries) {
            ChannelType kind = LnetEntryText.kindOf(entry, FilterTargetType.ITEMS);
            if (!kind.in(state.types()) || state.batches().get(kind.ordinal()) != 0) {
                return List.of();
            }
        }
        return entries;
    }

    private static List<String> settings(ChannelState state) {
        List<String> out = new ArrayList<>();
        if (state.tickDelay() != 20) {
            out.add("every " + state.tickDelay() + (state.tickDelay() == 1 ? " tick" : " ticks"));
        }
        if (state.priority() != 0) {
            out.add("priority " + state.priority());
        }
        switch (state.distributionMode()) {
            case NEAREST_FIRST -> out.add("nearest first");
            case FARTHEST_FIRST -> out.add("farthest first");
            case ROUND_ROBIN -> out.add("equal distribution");
            case PRIORITY_ROBIN -> out.add("priority robin");
            case PRIORITY -> {
            }
        }
        if (state.redstoneMode() != RedstoneMode.IGNORED) {
            out.add("when redstone " + state.redstoneMode().name().toLowerCase(Locale.ROOT));
        }
        if (state.resourceRoundRobin()) {
            out.add("rotate resources");
        }
        if (state.filterMode() == FilterMode.MATCH_ALL) {
            out.add("match all filters");
        }
        if (state.recipeSets() > 0) {
            out.add(state.recipeSets() + (state.recipeSets() == 1 ? " recipe set" : " recipe sets"));
        }
        if (!state.enabled()) {
            out.add("off");
        }
        return out;
    }

    private static String side(Optional<IoSide> direction) {
        IoSide side = direction.orElse(IoSide.ALL);
        if (side == IoSide.ALL) {
            return "all sides";
        }
        return side == IoSide.NONE ? "no side" : "side " + side.key();
    }

    private static IoSide sideOf(String word) throws IOException {
        for (IoSide side : IoSide.values()) {
            if (side.direction() != null && side.key().equals(word)) {
                return side;
            }
        }
        throw new IOException("unknown side '" + word + "'");
    }

    private static String amount(ChannelType type, int batch) {
        if (batch == 0) {
            return "max";
        }
        return batch + (type == ChannelType.FLUID || type == ChannelType.CHEMICAL ? "mb" : "");
    }
}
