package me.almana.logisticsnetworks.client.lnet;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record LnetNetworkFile(String networkName, List<LnetNetworkFile.NodeEntry> nodes) {

    private static final String EXTENSION = ".lnet";
    private static final String FILE_VERSION = "1.0";

    public record NodeEntry(String label, boolean visible, CompoundTag clipboardTag) {
    }

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("logistics-network").resolve("networks");
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
            out.append("clipboard=").append(withoutNetworkId(node.clipboardTag())).append('\n');
        }
        return out.toString();
    }

    private static CompoundTag withoutNetworkId(CompoundTag source) {
        CompoundTag root = source.copy();
        root.remove("network_id");
        return root;
    }

    public static LnetNetworkFile readString(String text) throws IOException {
        return LnetLegacyFormat.read(text);
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
}
