package me.almana.logisticsnetworks.client.lnet;

import me.almana.logisticsnetworks.component.ClipboardSnapshot;
import me.almana.logisticsnetworks.component.ClipboardSnapshot.ChannelState;
import me.almana.logisticsnetworks.component.ComponentCodecs;
import me.almana.logisticsnetworks.component.FilterSettings;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.data.NodeClipboardConfig;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LnetDocument {

    static final String HEADER = "lnet 2";
    private static final int MAX_DEPTH = 256;
    private static final Pattern CHANNEL =
            Pattern.compile("[Cc]hannel\\s+(\\d+)\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\")?\\s*:(.*)");

    private final HolderLookup.Provider registries;
    private final List<LnetNetworkFile.NodeEntry> nodes = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();
    private final Set<String> labels = new HashSet<>();
    private String network = "";
    private int lineNumber;
    @Nullable
    private NodeDraft node;
    @Nullable
    private LnetChannelText.ChannelDraft channel;

    private static final class NodeDraft {
        final String label;
        final List<ItemStack> upgrades = new ArrayList<>();
        final LnetChannelText.ChannelDraft[] channels =
                new LnetChannelText.ChannelDraft[LogisticsNodeEntity.CHANNEL_COUNT];
        boolean hidden;

        NodeDraft(String label) {
            this.label = label;
        }
    }

    private LnetDocument(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    static boolean isV2(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return trimmed.startsWith("lnet ");
            }
        }
        return false;
    }

    static LnetNetworkFile read(String text, HolderLookup.Provider registries) throws IOException {
        LnetDocument document = new LnetDocument(registries);
        String[] lines = text.split("\\R", -1);
        boolean header = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            document.lineNumber = i + 1;
            if (!header) {
                if (!line.equals(HEADER)) {
                    throw new IOException("Line " + (i + 1) + ": expected '" + HEADER + "'");
                }
                header = true;
                continue;
            }
            try {
                if (LnetEntryText.depth(line) > MAX_DEPTH) {
                    throw new IOException("nesting deeper than " + MAX_DEPTH);
                }
                document.readLine(line);
            } catch (IOException e) {
                throw new IOException("Line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return document.finish();
    }

    private LnetNetworkFile finish() throws IOException {
        finishNode();
        if (network.isBlank()) {
            throw new IOException("Missing 'network:' line");
        }
        if (nodes.isEmpty()) {
            throw new IOException("No [Label] nodes");
        }
        return new LnetNetworkFile(network, List.copyOf(nodes), List.copyOf(skipped));
    }

    private void readLine(String line) throws IOException {
        if (line.startsWith("network:")) {
            if (node != null) {
                throw new IOException("'network:' must come before the first node");
            }
            network = line.substring("network:".length()).trim();
        } else if (line.startsWith("[")) {
            startNode(line);
        } else if (node == null) {
            throw new IOException("expected 'network:' or a [Label] line, got '" + line + "'");
        } else if (line.equals("hidden")) {
            node.hidden = true;
        } else if (line.startsWith("upgrades:")) {
            readUpgrades(line.substring("upgrades:".length()).trim());
        } else {
            readChannelLine(line);
        }
    }

    private void readChannelLine(String line) throws IOException {
        Matcher header = CHANNEL.matcher(line);
        if (header.matches()) {
            startChannel(header);
            return;
        }
        if (channel == null) {
            throw new IOException("expected a Channel line, got '" + line + "'");
        }
        if (line.startsWith("recipe:")) {
            readRecipe(line.substring("recipe:".length()).trim());
            return;
        }
        channel.recipeTarget = -1;
        if (line.startsWith("raw filter:")) {
            readRawFilter(line.substring("raw filter:".length()).trim());
        } else if (line.matches("(allow|block)\\b.*")) {
            readFilterLine(line);
        } else {
            channel.readClauses(line, registries, this::skip);
        }
    }

    private void readFilterLine(String line) throws IOException {
        LnetFilterText.FilterDraft filter = LnetFilterText.readFilter(line, channel.mode, registries, this::skip);
        channel.addFilter(filter);
        if (filter.entries != null) {
            channel.recipeTarget = channel.filters.size() - 1;
        }
    }

    private void startNode(String line) throws IOException {
        int end = line.lastIndexOf(']');
        if (end < 1) {
            throw new IOException("node label needs a closing ']'");
        }
        if (!line.substring(end + 1).isBlank()) {
            throw new IOException("unexpected text after ']'");
        }
        String label = line.substring(1, end).trim();
        if (label.isEmpty()) {
            throw new IOException("empty node label");
        }
        finishNode();
        if (!labels.add(label)) {
            throw new IOException("duplicate node label '" + label + "'");
        }
        node = new NodeDraft(label);
    }

    private void startChannel(Matcher header) throws IOException {
        int number = LnetEntryText.number(header.group(1));
        if (number < 1 || number > LogisticsNodeEntity.CHANNEL_COUNT) {
            throw new IOException("channel number must be 1-" + LogisticsNodeEntity.CHANNEL_COUNT);
        }
        if (node.channels[number - 1] != null) {
            throw new IOException("channel " + number + " listed twice");
        }
        String name = header.group(2) == null ? "" : header.group(2).replaceAll("\\\\(.)", "$1");
        channel = new LnetChannelText.ChannelDraft(number - 1, name);
        node.channels[number - 1] = channel;
        String rest = header.group(3).trim();
        if (!rest.isEmpty()) {
            channel.readClauses(rest, registries, this::skip);
        }
    }

    private void readUpgrades(String body) throws IOException {
        for (String text : LnetEntryText.split(body, ",")) {
            try {
                node.upgrades.add(LnetEntryText.readItem(text, registries));
            } catch (LnetEntryText.MissingMod e) {
                skip(e.getMessage());
            }
        }
        if (node.upgrades.size() > LogisticsNodeEntity.UPGRADE_SLOT_COUNT) {
            throw new IOException("more than " + LogisticsNodeEntity.UPGRADE_SLOT_COUNT + " upgrades");
        }
    }

    private void readRecipe(String body) throws IOException {
        if (channel.recipeTarget < 0) {
            channel.addFilter(LnetFilterText.FilterDraft.general(FilterSettings.DEFAULT));
            channel.recipeTarget = channel.filters.size() - 1;
        }
        channel.filters.get(channel.recipeTarget).addCard(body, registries, this::skip);
    }

    private void readRawFilter(String body) throws IOException {
        try {
            channel.addFilter(LnetFilterText.readRaw(body, registries));
        } catch (LnetEntryText.MissingMod e) {
            skip(e.getMessage());
        }
    }

    private void skip(String message) {
        skipped.add("Line " + lineNumber + ": " + message);
    }

    private void finishNode() throws IOException {
        if (node == null) {
            return;
        }
        List<ChannelState> states = new ArrayList<>();
        List<ClipboardSnapshot.FilterSlot> filters = new ArrayList<>();
        for (int i = 0; i < LogisticsNodeEntity.CHANNEL_COUNT; i++) {
            LnetChannelText.ChannelDraft draft = node.channels[i];
            if (draft == null) {
                states.add(ChannelState.DEFAULT);
                continue;
            }
            states.add(draft.state(node.label));
            List<ItemStack> stacks = draft.filterStacks();
            for (int slot = 0; slot < stacks.size(); slot++) {
                filters.add(new ClipboardSnapshot.FilterSlot(i, slot, StackSnapshot.of(stacks.get(slot))));
            }
        }
        List<ClipboardSnapshot.ItemSlot> upgrades = new ArrayList<>();
        for (int slot = 0; slot < node.upgrades.size(); slot++) {
            upgrades.add(new ClipboardSnapshot.ItemSlot(slot, StackSnapshot.of(node.upgrades.get(slot))));
        }
        ClipboardSnapshot snapshot = new ClipboardSnapshot(states, filters, upgrades, Optional.empty(),
                Optional.of(network), !node.hidden, node.label);
        CompoundTag tag = (CompoundTag) ComponentCodecs.encode(NodeClipboardConfig.CODEC, registries, snapshot);
        nodes.add(new LnetNetworkFile.NodeEntry(node.label, !node.hidden, tag));
        node = null;
        channel = null;
    }

    static String write(String networkName, List<LnetNetworkFile.NodeEntry> entries,
            HolderLookup.Provider registries) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add("network: " + LnetEntryText.oneLine(networkName));
        for (LnetNetworkFile.NodeEntry entry : entries) {
            writeNode(lines, entry, registries);
        }
        return String.join("\n", lines) + "\n";
    }

    private static void writeNode(List<String> lines, LnetNetworkFile.NodeEntry entry,
            HolderLookup.Provider registries) throws IOException {
        ClipboardSnapshot snapshot = NodeClipboardConfig.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), entry.clipboardTag())
                .getOrThrow(error -> new IOException("Bad clipboard for '" + entry.label() + "': " + error));
        lines.add("");
        lines.add("[" + LnetEntryText.oneLine(entry.label()) + "]");
        if (!entry.visible()) {
            lines.add("hidden");
        }
        List<String> upgrades = snapshot.upgrades().stream()
                .sorted(Comparator.comparingInt(ClipboardSnapshot.ItemSlot::slot))
                .map(upgrade -> LnetEntryText.writeItem(upgrade.stack().toStack(), registries))
                .toList();
        if (!upgrades.isEmpty()) {
            lines.add("upgrades: " + String.join(", ", upgrades));
        }
        for (int i = 0; i < snapshot.channels().size(); i++) {
            int index = i;
            ChannelState state = snapshot.channels().get(i);
            List<ItemStack> filters = snapshot.filters().stream()
                    .filter(filter -> filter.channel() == index)
                    .sorted(Comparator.comparingInt(ClipboardSnapshot.FilterSlot::slot))
                    .map(filter -> filter.stack().toStack())
                    .toList();
            if (!state.equals(ChannelState.DEFAULT) || !filters.isEmpty()) {
                LnetChannelText.write(lines, i, state, filters, registries);
            }
        }
    }

}
