package me.almana.logisticsnetworks.upgrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.almana.logisticsnetworks.Logisticsnetworks;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class UpgradeLimitsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(Logisticsnetworks.MOD_ID);
    private static final String FILE_NAME = "upgrades.json";
    private static final String DIR_NAME = "logistics-network";
    private static final String[] TIER_KEYS = { "none", "iron", "gold", "diamond", "netherite" };

    private static final TierLimits[] TIERS = new TierLimits[5];
    private static boolean loaded = false;

    private UpgradeLimitsConfig() {
    }

    public static void load() {
        if (loaded)
            return;

        Path dirPath = FMLPaths.CONFIGDIR.get().resolve(DIR_NAME);
        File dir = dirPath.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Path configPath = dirPath.resolve(FILE_NAME);
        File configFile = configPath.toFile();

        if (!configFile.exists()) {
            LOGGER.info("Config file not found, generating default at {}", configPath);
            loadDefaults();
            generateDefault(configFile);
            return;
        }

        try (FileReader reader = new FileReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            for (int i = 0; i < TIER_KEYS.length; i++) {
                JsonObject tier = root.getAsJsonObject(TIER_KEYS[i]);
                if (tier == null) {
                    LOGGER.warn("Missing tier '{}' in config, using defaults for this tier", TIER_KEYS[i]);
                    TIERS[i] = defaultForTier(i);
                } else {
                    TIERS[i] = parseTier(tier, i);
                }
            }

            loaded = true;
            LOGGER.info("Loaded {} successfully", FILE_NAME);
        } catch (Exception e) {
            LOGGER.error("Failed to parse {}, using defaults", FILE_NAME, e);
            loadDefaults();
        }
    }

    private static void generateDefault(File file) {
        try {
            JsonObject root = new JsonObject();
            for (int i = 0; i < TIER_KEYS.length; i++) {
                JsonObject tierObj = new JsonObject();
                TierLimits limits = TIERS[i];
                tierObj.addProperty("minTicks", limits.minTicks());
                tierObj.addProperty("itemBatch", limits.itemBatch());
                tierObj.addProperty("fluidBatch", limits.fluidBatch());
                tierObj.addProperty("energyBatch", limits.energyBatch());
                tierObj.addProperty("chemicalBatch", limits.chemicalBatch());
                tierObj.addProperty("sourceBatch", limits.sourceBatch());
                root.add(TIER_KEYS[i], tierObj);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate default config {}", FILE_NAME, e);
        }
    }

    private static TierLimits parseTier(JsonObject obj, int tierIndex) {
        TierLimits defaults = defaultForTier(tierIndex);
        return new TierLimits(
                getInt(obj, "minTicks", defaults.minTicks()),
                getInt(obj, "itemBatch", defaults.itemBatch()),
                getInt(obj, "fluidBatch", defaults.fluidBatch()),
                getInt(obj, "energyBatch", defaults.energyBatch()),
                getInt(obj, "chemicalBatch", defaults.chemicalBatch()),
                getInt(obj, "sourceBatch", defaults.sourceBatch()));
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsInt() : fallback;
    }

    private static void loadDefaults() {
        for (int i = 0; i < TIERS.length; i++) {
            TIERS[i] = defaultForTier(i);
        }
        loaded = true;
    }

    private static TierLimits defaultForTier(int tier) {
        return switch (tier) {
            case 1 -> new TierLimits(10, 16, 1_000, 10_000, 1_000, 1_000);
            case 2 -> new TierLimits(5, 32, 5_000, 50_000, 5_000, 5_000);
            case 3 -> new TierLimits(1, 64, 20_000, 250_000, 20_000, 20_000);
            case 4 -> new TierLimits(1, 10_000, 1_000_000, Integer.MAX_VALUE, 1_000_000, 1_000_000);
            default -> new TierLimits(20, 8, 500, 2_000, 500, 500);
        };
    }

    public static TierLimits get(int tier) {
        if (!loaded)
            load();
        if (tier < 0 || tier >= TIERS.length)
            return TIERS[0];
        return TIERS[tier];
    }

    public record TierLimits(int minTicks, int itemBatch, int fluidBatch, int energyBatch, int chemicalBatch,
            int sourceBatch) {
    }
}
