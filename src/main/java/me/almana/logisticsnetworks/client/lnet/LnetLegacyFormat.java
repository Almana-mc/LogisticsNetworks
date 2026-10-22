package me.almana.logisticsnetworks.client.lnet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class LnetLegacyFormat {

    private static final String FORMAT_LINE = "format=lnet";
    private static final String FILE_VERSION = "1.0";
    private static final String LEGACY_VERSION_LINE = "lnet=1";

    private LnetLegacyFormat() {
    }

    static LnetNetworkFile read(String text) throws IOException {
        String[] lines = text.split("\\R");
        if (lines.length == 0 || !hasValidHeader(lines[0].trim())) {
            throw new IOException("Invalid lnet header");
        }

        String networkName = "";
        List<LnetNetworkFile.NodeEntry> nodes = new ArrayList<>();
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
                        clipboard = TagParser.parseTag(value);
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

    private static LnetNetworkFile.NodeEntry createNode(String label, boolean visible, CompoundTag clipboard)
            throws IOException {
        if (label == null || label.isBlank()) {
            throw new IOException("Missing label");
        }
        if (clipboard == null) {
            throw new IOException("Missing label clipboard");
        }
        return new LnetNetworkFile.NodeEntry(label, visible, clipboard.copy());
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
