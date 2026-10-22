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

        MissingMod(ResourceLocation id, ChannelType kind) {
            super("'" + id + "' needs mod '" + id.getNamespace() + "'");
            this.kind = kind;
        }
    }

    private static final Pattern BATCH = Pattern.compile("(\\d+)(mb)? of (.+)");
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
                depth--;
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
            return new ItemStack(result.item(), 1, result.components());
        } catch (CommandSyntaxException e) {
            ResourceLocation id = idAt(e.getInput(), e.getCursor());
            if (id != null && !ModList.get().isLoaded(id.getNamespace())) {
                throw new MissingMod(id, ChannelType.ITEM);
            }
            throw new IOException(e.getRawMessage().getString() + " in '" + text + "'");
        }
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

    static String readFluid(String text) throws IOException {
        ResourceLocation id = id(text, "fluid");
        if (BuiltInRegistries.FLUID.containsKey(id)) {
            return id.toString();
        }
        if (!ModList.get().isLoaded(id.getNamespace())) {
            throw new MissingMod(id, ChannelType.FLUID);
        }
        throw new IOException("unknown fluid '" + id + "'");
    }

    static String readChemical(String text) throws IOException {
        ResourceLocation id = id(text, "chemical");
        if (!MekanismCompat.isLoaded() || !ModList.get().isLoaded(id.getNamespace())) {
            throw new MissingMod(id, ChannelType.CHEMICAL);
        }
        if (!MekanismCompat.isValidChemicalId(id.toString())) {
            throw new IOException("unknown chemical '" + id + "'");
        }
        return id.toString();
    }

    static ResourceLocation id(String text, String kind) throws IOException {
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) {
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

        ChannelType kind = mb ? ChannelType.FLUID : ChannelType.ITEM;
        if (head.startsWith("fluid ")) {
            kind = ChannelType.FLUID;
            head = head.substring("fluid ".length()).trim();
        } else if (head.startsWith("chemical ")) {
            kind = ChannelType.CHEMICAL;
            head = head.substring("chemical ".length()).trim();
        }

        StackSnapshot item = null;
        String fluid = null;
        String chemical = null;
        String tag = null;
        FilterTargetType tagKind = null;
        if (head.startsWith("#")) {
            tag = id(head.substring(1), "tag").toString();
            tagKind = targetOf(kind);
        } else if (kind == ChannelType.FLUID) {
            fluid = readFluid(head);
        } else if (kind == ChannelType.CHEMICAL) {
            chemical = readChemical(head);
        } else {
            item = StackSnapshot.of(readItem(head, registries));
        }

        GeneralFilterEntry entry = new GeneralFilterEntry(slot, item, fluid, chemical, tag,
                new GeneralFilterEntry.EntryCounts(0, batch, 0), GeneralFilterEntry.SlotMapping.EMPTY, null,
                GeneralFilterEntry.NbtConstraints.EMPTY, null, tagKind);
        return modifiers.isEmpty() ? entry : readModifiers(entry, modifiers, mode);
    }

    private static GeneralFilterEntry readModifiers(GeneralFilterEntry entry, String text,
            @Nullable ChannelMode mode) throws IOException {
        int stock = 0;
        GeneralFilterEntry.SlotMapping slots = GeneralFilterEntry.SlotMapping.EMPTY;
        List<NbtCriterion> rules = List.of();
        boolean any = false;
        Optional<Boolean> strict = Optional.empty();
        String raw = "";
        for (String part : split(text, ",")) {
            if (part.startsWith("keep ")) {
                if (mode != ChannelMode.EXPORT) {
                    throw new IOException("'keep' only works on send channels, use 'fill to'");
                }
                stock = number(part.substring("keep ".length()));
            } else if (part.startsWith("fill to ")) {
                if (mode != ChannelMode.IMPORT) {
                    throw new IOException("'fill to' only works on receive channels, use 'keep'");
                }
                stock = number(part.substring("fill to ".length()));
            } else if (part.startsWith("slots ")) {
                String expression = part.substring("slots ".length()).trim();
                BitSet bits = SlotExpressionUtil.parseSlots(expression);
                if (bits == null || bits.isEmpty()) {
                    throw new IOException("bad slots '" + expression + "'");
                }
                slots = new GeneralFilterEntry.SlotMapping(bits.stream().boxed().toList(), expression);
            } else if (part.startsWith("where ")) {
                String body = part.substring("where ".length());
                List<String> ands = split(body, " and ");
                List<String> ors = split(body, " or ");
                if (ands.size() > 1 && ors.size() > 1) {
                    throw new IOException("'where' mixes 'and' and 'or'");
                }
                any = ors.size() > 1;
                rules = new ArrayList<>();
                for (String rule : any ? ors : ands) {
                    rules.add(readRule(rule));
                }
            } else if (part.equals("strict")) {
                strict = Optional.of(true);
            } else if (part.equals("not strict")) {
                strict = Optional.of(false);
            } else if (part.startsWith("matches ")) {
                raw = part.substring("matches ".length()).trim();
                readTag(raw);
            } else {
                throw new IOException("unknown modifier '" + part + "'");
            }
        }
        return entry.withCounts(new GeneralFilterEntry.EntryCounts(0, entry.counts().batch(), stock))
                .withSlotMapping(slots)
                .withNbt(new GeneralFilterEntry.NbtConstraints(rules, any, strict, raw));
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

    static String writeEntry(GeneralFilterEntry entry, FilterTargetType target, ChannelMode mode,
            HolderLookup.Provider registries) {
        ChannelType kind = kindOf(entry, target);
        int batch = entry.counts().batch();
        StringBuilder out = new StringBuilder();
        if (batch > 0) {
            out.append(batch).append(kind == ChannelType.ITEM ? "" : "mb").append(" of ");
        }
        if (kind == ChannelType.CHEMICAL) {
            out.append("chemical ");
        } else if (kind == ChannelType.FLUID && batch == 0) {
            out.append("fluid ");
        }
        String tag = FilterTagUtil.normalizeTag(entry.tag());
        if (entry.fluidId() != null) {
            out.append(shortId(entry.fluidId()));
        } else if (entry.chemicalId() != null) {
            out.append(shortId(entry.chemicalId()));
        } else if (tag != null) {
            out.append('#').append(shortId(tag));
        } else {
            out.append(writeItem(entry.item().toStack(), registries));
        }
        List<String> modifiers = modifiers(entry, mode);
        if (!modifiers.isEmpty()) {
            out.append(" (").append(String.join(", ", modifiers)).append(')');
        }
        return out.toString();
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
        if (entry.fluidId() != null) {
            return ChannelType.FLUID;
        }
        if (entry.chemicalId() != null) {
            return ChannelType.CHEMICAL;
        }
        if (FilterTagUtil.normalizeTag(entry.tag()) != null) {
            return channelOf(entry.tagKind() != null ? entry.tagKind() : target);
        }
        return ChannelType.ITEM;
    }

    static boolean isBlank(GeneralFilterEntry entry) {
        return entry.item() == null && entry.fluidId() == null && entry.chemicalId() == null
                && FilterTagUtil.normalizeTag(entry.tag()) == null;
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
