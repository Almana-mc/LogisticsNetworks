package me.almana.logisticsnetworks.client.lnet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record LnetNetworkFile(String networkName, List<LnetNetworkFile.NodeEntry> nodes) {

    private static final String EXTENSION = ".lnet";
    private static final String FORMAT_LINE = "format=lnet";
    private static final String FILE_VERSION = "1.0";
    private static final String LEGACY_VERSION_LINE = "lnet=1";

    public record NodeEntry(String label, boolean visible, CompoundTag clipboardTag) {
    }

    public static Path directory() {
        return LnetPaths.configDirectory().resolve("logistics-network").resolve("networks");
    }

    public static List<Path> listFiles() throws IOException {
        Path dir = directory();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    public static Path write(String networkName, List<NodeEntry> nodes) throws IOException {
        Files.createDirectories(directory());
        Path path = filePath(networkName);
        Files.writeString(path, new LnetNetworkFile(networkName, nodes).writeString(), StandardCharsets.UTF_8);
        return path;
    }

    public static LnetNetworkFile read(Path path) throws IOException {
        return readString(Files.readString(path, StandardCharsets.UTF_8));
    }

    public String writeString() {
        StringBuilder out = new StringBuilder();
        out.append("version=").append(FILE_VERSION).append('\n');
        out.append("network=").append(escape(networkName)).append('\n');
        for (NodeEntry node : nodes) {
            out.append("label=").append(escape(node.label())).append('\n');
            if (!node.visible()) {
                out.append("visible=false\n");
            }
            out.append("clipboard=").append(compactClipboard(node.clipboardTag())).append('\n');
        }
        return out.toString();
    }

    private static CompoundTag compactClipboard(CompoundTag source) {
        CompoundTag root = source.copy();
        root.remove("version");
        root.remove("network_id");
        if (LnetNbtAccess.getBoolean(root, "renderVisible", true)) {
            root.remove("renderVisible");
        }

        ListTag compactChannels = new ListTag();
        for (Tag tag : LnetNbtAccess.getList(root, "channels")) {
            if (!(tag instanceof CompoundTag channel)) {
                continue;
            }

            CompoundTag entry = new CompoundTag();
            int index = LnetNbtAccess.getInt(channel, "index", -1);
            if (index < 0) {
                continue;
            }
            entry.putInt("index", index);
            boolean changed = false;
            changed |= copyBoolean(channel, entry, "enabled", false);
            changed |= copyString(channel, entry, "mode", "IMPORT");
            changed |= copyString(channel, entry, "type", "ITEM");
            changed |= copyInt(channel, entry, "batch", 8);
            changed |= copyInt(channel, entry, "delay", 20);
            changed |= copyString(channel, entry, "io", "up");
            changed |= copyString(channel, entry, "redstone", "ALWAYS_ON");
            changed |= copyString(channel, entry, "distribution", "PRIORITY");
            changed |= copyString(channel, entry, "filter_mode", "MATCH_ANY");
            changed |= copyInt(channel, entry, "priority", 0);
            if (channel.contains("name") && !LnetNbtAccess.getString(channel, "name", "").isEmpty()) {
                entry.putString("name", LnetNbtAccess.getString(channel, "name", ""));
                changed = true;
            }
            if (changed) {
                compactChannels.add(entry);
            }
        }
        root.put("channels", compactChannels);
        return root;
    }

    private static boolean copyBoolean(CompoundTag source, CompoundTag target, String key, boolean fallback) {
        boolean value = LnetNbtAccess.getBoolean(source, key, fallback);
        if (value == fallback) {
            return false;
        }
        target.putBoolean(key, value);
        return true;
    }

    private static boolean copyInt(CompoundTag source, CompoundTag target, String key, int fallback) {
        int value = LnetNbtAccess.getInt(source, key, fallback);
        if (value == fallback) {
            return false;
        }
        target.putInt(key, value);
        return true;
    }

    private static boolean copyString(CompoundTag source, CompoundTag target, String key, String fallback) {
        String value = LnetNbtAccess.getString(source, key, fallback);
        if (value.equals(fallback)) {
            return false;
        }
        target.putString(key, value);
        return true;
    }

    public static LnetNetworkFile readString(String text) throws IOException {
        String[] lines = text.split("\\R");
        if (lines.length == 0 || !hasValidHeader(lines[0].trim())) {
            throw new IOException("Invalid lnet header");
        }

        String networkName = "";
        List<NodeEntry> nodes = new ArrayList<>();
        String label = null;
        boolean visible = true;
        CompoundTag clipboard = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            int split = line.indexOf('=');
            String key = split >= 0 ? line.substring(0, split) : line;
            String value = split >= 0 ? line.substring(split + 1) : "";

            switch (key) {
                case "format" -> {
                    if (!"lnet".equals(value)) {
                        throw new IOException("Invalid lnet format");
                    }
                }
                case "version" -> {
                    if (!FILE_VERSION.equals(value)) {
                        throw new IOException("Unsupported lnet version");
                    }
                }
                case "lnet" -> {
                    if (!"1".equals(value)) {
                        throw new IOException("Unsupported lnet version");
                    }
                }
                case "network", "n" -> networkName = unescape(value);
                case "label", "node" -> {
                    if (label != null) {
                        nodes.add(createNode(label, visible, clipboard));
                    }
                    label = unescape(value);
                    visible = true;
                    clipboard = null;
                }
                case "visible" -> visible = !"false".equalsIgnoreCase(value) && !"0".equals(value);
                case "v" -> visible = !"0".equals(value);
                case "clipboard", "c" -> {
                    try {
                        clipboard = LnetNbtAccess.parse(value);
                    } catch (Exception e) {
                        throw new IOException("Invalid clipboard SNBT", e);
                    }
                }
                case "end" -> {
                    nodes.add(createNode(label, visible, clipboard));
                    label = null;
                    visible = true;
                    clipboard = null;
                }
                default -> {
                }
            }
        }

        if (label != null) {
            nodes.add(createNode(label, visible, clipboard));
        }

        if (networkName.isBlank()) {
            throw new IOException("Missing network name");
        }
        if (nodes.isEmpty()) {
            throw new IOException("No label entries");
        }
        return new LnetNetworkFile(networkName, List.copyOf(nodes));
    }

    private static boolean hasValidHeader(String line) {
        return line.equals("version=" + FILE_VERSION)
                || FORMAT_LINE.equals(line)
                || LEGACY_VERSION_LINE.equals(line);
    }

    private static NodeEntry createNode(String label, boolean visible, CompoundTag clipboard) throws IOException {
        if (label == null || label.isBlank()) {
            throw new IOException("Missing label");
        }
        if (clipboard == null) {
            throw new IOException("Missing label clipboard");
        }
        return new NodeEntry(label, visible, clipboard.copy());
    }

    private static Path filePath(String networkName) {
        String base = sanitizeFileName(networkName);
        return directory().resolve(base + EXTENSION);
    }

    private static String sanitizeFileName(String name) {
        String clean = name.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        clean = clean.replaceAll("_+", "_");
        if (clean.isBlank()) {
            return "network";
        }
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                switch (ch) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    default -> out.append(ch);
                }
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                out.append(ch);
            }
        }
        if (escaped) {
            out.append('\\');
        }
        return out.toString();
    }
}
