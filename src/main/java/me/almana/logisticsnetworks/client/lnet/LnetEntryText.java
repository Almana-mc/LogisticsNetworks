package me.almana.logisticsnetworks.client.lnet;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.NbtCriterion;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.filter.FilterTagUtil;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NbtPath;
import me.almana.logisticsnetworks.filter.SlotExpressionUtil;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LnetEntryText {

    static final class MissingMod extends IOException {
        final ChannelType kind;

        MissingMod(ResourceLocation id, String mod, ChannelType kind) {
            super("'" + id + "' needs mod '" + mod + "'");
            this.kind = kind;
        }
    }

    private static final Pattern BATCH = Pattern.compile("(\\d+)(mb)? of (.+)");
    private static final Pattern QUOTED_ID = Pattern.compile("([\"'])(([a-z0-9_.-]+):[a-z0-9_./-]+)\\1");
    private static final String[] OPERATORS = {">=", "<=", "!=", "=", ">", "<"};

    private LnetEntryText() {
    }

    // Skips brackets, parens, braces, quotes
    static int find(String text, String token, int from) {
        int depth = 0;
        char quote = 0;
        for (int i = from; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote != 0) {
                if (ch == '\\') {
                    i++;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (depth == 0 && text.startsWith(token, i)) {
                return i;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == '[' || ch == '(' || ch == '{') {
                depth++;
            } else if (ch == ']' || ch == ')' || ch == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return -1;
    }

    static List<String> split(String text, String delimiter) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int at = find(text, delimiter, 0); at >= 0; at = find(text, delimiter, start)) {
            parts.add(text.substring(start, at).trim());
            start = at + delimiter.length();
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    static int number(String text) throws IOException {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IOException("'" + text.trim() + "' is not a number");
        }
    }

    static String writeItem(ItemStack stack, HolderLookup.Provider registries) {
        return shortId(new ItemInput(stack.getItemHolder(), stack.getComponentsPatch()).serialize(registries));
    }

    static ItemStack readItem(String text, HolderLookup.Provider registries) throws IOException {
        if (text.isEmpty()) {
            throw new IOException("missing item");
        }
        StringReader reader = new StringReader(text);
        try {
            ItemParser.ItemResult result = new ItemParser(registries).parse(reader);
            if (reader.canRead()) {
                throw new IOException("unexpected '" + reader.getRemaining() + "' after item");
            }
            if (result.item().value() == Items.AIR) {
                throw new IOException("'air' is not an item");
            }
            return new ItemStack(result.item(), 1, result.components());
        } catch (CommandSyntaxException e) {
            ResourceLocation at = atIdPosition(e.getInput(), e.getCursor())
                    ? idAt(e.getInput(), e.getCursor()) : null;
            if (at != null && !loaded(at.getNamespace())) {
                throw new MissingMod(at, at.getNamespace(), ChannelType.ITEM);
            }
            Matcher ids = QUOTED_ID.matcher(text);
            while (ids.find()) {
                if (!loaded(ids.group(3))) {
                    throw new MissingMod(ResourceLocation.parse(ids.group(2)), ids.group(3), ChannelType.ITEM);
                }
            }
            throw new IOException(e.getRawMessage().getString() + " in '" + text + "'");
        }
    }

    private static boolean atIdPosition(@Nullable String input, int cursor) {
        if (input == null || cursor < 0 || cursor > input.length()) {
            return false;
        }
        int i = cursor - 1;
        while (i >= 0 && Character.isWhitespace(input.charAt(i))) {
            i--;
        }
        return i < 0 || "[,!".indexOf(input.charAt(i)) >= 0;
    }

    @Nullable
    private static ResourceLocation idAt(@Nullable String input, int cursor) {
        if (input == null || cursor < 0) {
            return null;
        }
        StringReader reader = new StringReader(input);
        reader.setCursor(cursor);
        try {
            return ResourceLocation.read(reader);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static boolean loaded(String namespace) {
        return ModList.get().isLoaded(namespace) || namespace.equals("c");
    }

    static String readFluid(String text) throws IOException {
        ResourceLocation id = id(text, "fluid");
        if (BuiltInRegistries.FLUID.containsKey(id)) {
            return id.toString();
        }
        if (!loaded(id.getNamespace())) {
            throw new MissingMod(id, id.getNamespace(), ChannelType.FLUID);
        }
        throw new IOException("unknown fluid '" + id + "'");
    }

    static String readChemical(String text) throws IOException {
        ResourceLocation id = id(text, "chemical");
        if (!MekanismCompat.isLoaded() || !loaded(id.getNamespace())) {
            String mod = MekanismCompat.isLoaded() ? id.getNamespace() : "mekanism";
            throw new MissingMod(id, mod, ChannelType.CHEMICAL);
        }
        if (!MekanismCompat.isValidChemicalId(id.toString())) {
            throw new IOException("unknown chemical '" + id + "'");
        }
        return id.toString();
    }

    static ResourceLocation id(String text, String kind) throws IOException {
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null || id.getPath().isEmpty()) {
            throw new IOException("bad " + kind + " id '" + text + "'");
        }
        return id;
    }

    static GeneralFilterEntry readEntry(String text, int slot, @Nullable ChannelMode mode,
            HolderLookup.Provider registries) throws IOException {
        String head = text;
        String modifiers = "";
        int open = find(text, "(", 0);
        if (open >= 0) {
            if (!text.endsWith(")")) {
                throw new IOException("expected ')' at the end of '" + text + "'");
            }
            head = text.substring(0, open).trim();
            modifiers = text.substring(open + 1, text.length() - 1).trim();
        }

        int batch = 0;
        boolean mb = false;
        Matcher matcher = BATCH.matcher(head);
        if (matcher.matches()) {
            batch = number(matcher.group(1));
            mb = matcher.group(2) != null;
            head = matcher.group(3).trim();
        }
        GeneralFilterEntry entry = readTarget(head, batch, mb, slot, registries);
        GeneralFilterEntry result = modifiers.isEmpty() ? entry : readModifiers(entry, modifiers, mode);
        if (isBlank(result)) {
            throw new IOException("'any' needs a count, keep, fill to, slots, where or matches");
        }
        return result;
    }

    private static GeneralFilterEntry readTarget(String head, int batch, boolean mb, int slot,
            HolderLookup.Provider registries) throws IOException {
        ChannelType kind = mb ? ChannelType.FLUID : ChannelType.ITEM;
        boolean kindWord = true;
        if (head.startsWith("fluid ")) {
            kind = ChannelType.FLUID;
            head = head.substring("fluid ".length()).trim();
        } else if (head.startsWith("chemical ")) {
            kind = ChannelType.CHEMICAL;
            head = head.substring("chemical ".length()).trim();
        } else {
            kindWord = false;
        }

        StackSnapshot item = null;
        String fluid = null;
        String chemical = null;
        String tag = null;
        FilterTargetType tagKind = null;
        if (head.equals("any")) {
            if (mb) {
                throw new IOException("'any' takes a plain count, not mb");
            }
            if (kindWord) {
                throw new IOException("'any' takes no kind word");
            }
        } else if (head.startsWith("#")) {
            tag = id(head.substring(1), "tag").toString();
            tagKind = targetOf(kind);
        } else if (kind == ChannelType.FLUID) {
            fluid = readFluid(head);
        } else if (kind == ChannelType.CHEMICAL) {
            chemical = readChemical(head);
        } else {
            item = StackSnapshot.of(readItem(head, registries));
        }
        return new GeneralFilterEntry(slot, item, fluid, chemical, tag,
                new GeneralFilterEntry.EntryCounts(0, batch, 0), GeneralFilterEntry.SlotMapping.EMPTY, null,
                GeneralFilterEntry.NbtConstraints.EMPTY, null, tagKind);
    }

    private static GeneralFilterEntry readModifiers(GeneralFilterEntry entry, String text,
            @Nullable ChannelMode mode) throws IOException {
        int stock = 0;
        GeneralFilterEntry.SlotMapping slots = GeneralFilterEntry.SlotMapping.EMPTY;
        GeneralFilterEntry.NbtConstraints where = GeneralFilterEntry.NbtConstraints.EMPTY;
        Optional<Boolean> strict = Optional.empty();
        String raw = "";
        int matchesStart = matchesStart(text);
        if (matchesStart >= 0) {
            raw = text.substring(matchesStart).trim().substring("matches ".length()).trim();
            text = text.substring(0, Math.max(0, matchesStart - 1));
        }
        for (String part : text.isEmpty() ? List.<String>of() : split(text, ",")) {
            if (part.startsWith("keep ")) {
                if (mode != ChannelMode.EXPORT) {
                    throw new IOException("'keep' only works on send channels, use 'fill to'");
                }
                stock = amount(part.substring("keep ".length()));
            } else if (part.startsWith("fill to ")) {
                if (mode != ChannelMode.IMPORT) {
                    throw new IOException("'fill to' only works on receive channels, use 'keep'");
                }
                stock = amount(part.substring("fill to ".length()));
            } else if (part.startsWith("slots ")) {
                slots = readSlots(part.substring("slots ".length()).trim());
            } else if (part.startsWith("where ")) {
                where = readWhere(part.substring("where ".length()));
            } else if (part.equals("strict")) {
                strict = Optional.of(true);
            } else if (part.equals("not strict")) {
                strict = Optional.of(false);
            } else {
                throw new IOException("unknown modifier '" + part + "'");
            }
        }
        return entry.withCounts(new GeneralFilterEntry.EntryCounts(0, entry.counts().batch(), stock))
                .withSlotMapping(slots)
                .withNbt(new GeneralFilterEntry.NbtConstraints(where.rules(), where.matchAny(), strict, raw));
    }

    private static int matchesStart(String text) {
        int start = 0;
        while (true) {
            int comma = find(text, ",", start);
            int end = comma < 0 ? text.length() : comma;
            if (text.substring(start, end).trim().startsWith("matches ")) {
                return start;
            }
            if (comma < 0) {
                return -1;
            }
            start = comma + 1;
        }
    }

    private static int amount(String text) throws IOException {
        int value = number(text);
        if (value < 0) {
            throw new IOException("'" + text.trim() + "' can't be negative");
        }
        return value;
    }

    private static GeneralFilterEntry.SlotMapping readSlots(String expression) throws IOException {
        BitSet bits = SlotExpressionUtil.parseMask(expression);
        if (bits == null || bits.isEmpty()) {
            throw new IOException("bad slots '" + expression + "'");
        }
        return new GeneralFilterEntry.SlotMapping(bits.stream().boxed().toList(), expression);
    }

    private static GeneralFilterEntry.NbtConstraints readWhere(String body) throws IOException {
        List<String> ands = split(body, " and ");
        List<String> ors = split(body, " or ");
        if (ands.size() > 1 && ors.size() > 1) {
            throw new IOException("'where' mixes 'and' and 'or'");
        }
        boolean any = ors.size() > 1;
        List<NbtCriterion> rules = new ArrayList<>();
        for (String rule : any ? ors : ands) {
            rules.add(readRule(rule));
        }
        return new GeneralFilterEntry.NbtConstraints(rules, any, Optional.empty(), "");
    }

    private static NbtCriterion readRule(String text) throws IOException {
        int space = find(text, " ", 0);
        if (space < 0) {
            throw new IOException("rule needs '<path> <op> <value>': '" + text + "'");
        }
        NbtPath path = NbtPath.parseLenient(text.substring(0, space));
        String rest = text.substring(space + 1).trim();
        for (String operator : OPERATORS) {
            if (rest.startsWith(operator + " ")) {
                return new NbtCriterion(path, operator, readTag(rest.substring(operator.length()).trim()));
            }
        }
        throw new IOException("unknown operator in rule '" + text + "'");
    }

    static Tag readTag(String text) throws IOException {
        StringReader reader = new StringReader(text);
        try {
            Tag tag = new TagParser(reader).readValue();
            if (reader.canRead()) {
                throw new IOException("bad nbt value '" + text + "'");
            }
            return tag;
        } catch (CommandSyntaxException e) {
            throw new IOException("bad nbt value '" + text + "'");
        }
    }

    static boolean hasTarget(GeneralFilterEntry entry) {
        return entry.item() != null || present(entry.fluidId()) || present(entry.chemicalId())
                || FilterTagUtil.normalizeTag(entry.tag()) != null;
    }

    private static boolean present(@Nullable String id) {
        return id != null && !id.isEmpty();
    }

    static String writeEntry(GeneralFilterEntry entry, FilterTargetType target, ChannelMode mode,
            HolderLookup.Provider registries) {
        ChannelType kind = kindOf(entry, target);
        int batch = entry.counts().batch();
        boolean any = !hasTarget(entry);
        StringBuilder out = new StringBuilder();
        if (batch > 0) {
            out.append(batch).append(kind == ChannelType.ITEM || any ? "" : "mb").append(" of ");
        }
        out.append(any ? "any" : writeTarget(entry, kind, batch, registries));
        List<String> modifiers = modifiers(entry, mode);
        if (!modifiers.isEmpty()) {
            out.append(" (").append(String.join(", ", modifiers)).append(')');
        }
        return out.toString();
    }

    private static String writeTarget(GeneralFilterEntry entry, ChannelType kind, int batch,
            HolderLookup.Provider registries) {
        String prefix = kind == ChannelType.CHEMICAL ? "chemical "
                : kind == ChannelType.FLUID && batch == 0 ? "fluid " : "";
        String tag = FilterTagUtil.normalizeTag(entry.tag());
        if (present(entry.fluidId())) {
            return prefix + shortId(entry.fluidId());
        }
        if (present(entry.chemicalId())) {
            return prefix + shortId(entry.chemicalId());
        }
        if (tag != null) {
            return prefix + '#' + shortId(tag);
        }
        return writeItem(entry.item().toStack(), registries);
    }

    private static List<String> modifiers(GeneralFilterEntry entry, ChannelMode mode) {
        List<String> out = new ArrayList<>();
        int stock = entry.counts().stock() != 0 ? entry.counts().stock() : entry.counts().amount();
        if (stock > 0) {
            out.add((mode == ChannelMode.EXPORT ? "keep " : "fill to ") + stock);
        }
        GeneralFilterEntry.SlotMapping slots = entry.slotMapping();
        if (!slots.slots().isEmpty()) {
            String expression = slots.expression().isBlank()
                    ? SlotExpressionUtil.formatSlots(slots.slots())
                    : slots.expression();
            out.add("slots " + expression.trim().replaceAll("[,;\\s]+", " "));
        }
        GeneralFilterEntry.NbtConstraints nbt = entry.nbt();
        if (!nbt.rules().isEmpty()) {
            List<String> rules = nbt.rules().stream()
                    .map(rule -> rule.path() + " " + rule.operator() + " " + rule.value())
                    .toList();
            out.add("where " + String.join(nbt.matchAny() ? " or " : " and ", rules));
        }
        nbt.strict().ifPresent(strict -> out.add(strict ? "strict" : "not strict"));
        if (!nbt.raw().isBlank()) {
            out.add("matches " + nbt.raw().trim());
        }
        return out;
    }

    static ChannelType kindOf(GeneralFilterEntry entry, FilterTargetType target) {
        if (present(entry.fluidId())) {
            return ChannelType.FLUID;
        }
        if (present(entry.chemicalId())) {
            return ChannelType.CHEMICAL;
        }
        if (FilterTagUtil.normalizeTag(entry.tag()) != null) {
            return channelOf(entry.tagKind() != null ? entry.tagKind() : target);
        }
        return ChannelType.ITEM;
    }

    static boolean isBlank(GeneralFilterEntry entry) {
        int stock = entry.counts().stock() != 0 ? entry.counts().stock() : entry.counts().amount();
        return !hasTarget(entry) && entry.slotMapping().slots().isEmpty() && entry.nbt().rules().isEmpty()
                && entry.nbt().raw().isBlank() && entry.counts().batch() == 0 && stock == 0;
    }

    private static ChannelType channelOf(FilterTargetType target) {
        return switch (target) {
            case FLUIDS -> ChannelType.FLUID;
            case CHEMICALS -> ChannelType.CHEMICAL;
            case ITEMS -> ChannelType.ITEM;
        };
    }

    private static FilterTargetType targetOf(ChannelType kind) {
        return switch (kind) {
            case FLUID -> FilterTargetType.FLUIDS;
            case CHEMICAL -> FilterTargetType.CHEMICALS;
            default -> FilterTargetType.ITEMS;
        };
    }
}
