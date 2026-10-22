package me.almana.logisticsnetworks.client.lnet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record LnetNetworkFile(String networkName, List<LnetNetworkFile.NodeEntry> nodes, List<String> skipped) {

    private static final String EXTENSION = ".lnet";

    public record NodeEntry(String label, boolean visible, CompoundTag clipboardTag) {
    }

    public LnetNetworkFile(String networkName, List<NodeEntry> nodes) {
        this(networkName, nodes, List.of());
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

    public static Path write(String networkName, List<NodeEntry> nodes, HolderLookup.Provider registries)
            throws IOException {
        Files.createDirectories(directory());
        Path path = filePath(networkName);
        try {
            Files.writeString(path, LnetDocument.write(networkName, nodes, registries), StandardCharsets.UTF_8);
        } catch (RuntimeException | StackOverflowError e) {
            throw new IOException("Unwritable network: " + e, e);
        }
        return path;
    }

    public static LnetNetworkFile read(Path path, HolderLookup.Provider registries) throws IOException {
        return readString(Files.readString(path, StandardCharsets.UTF_8), registries);
    }

    public static LnetNetworkFile readString(String text, HolderLookup.Provider registries) throws IOException {
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        try {
            return LnetDocument.isV2(body) ? LnetDocument.read(body, registries) : LnetLegacyFormat.read(body);
        } catch (RuntimeException | StackOverflowError e) {
            throw new IOException("Unreadable file: " + e, e);
        }
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
}
