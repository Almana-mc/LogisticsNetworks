package me.almana.logisticsnetworks.client.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ThemeState {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "logisticsnetworks-theme.json";

    private static Theme active = Themes.DARK;
    private static final List<Runnable> listeners = new ArrayList<>();

    public static Theme active() {
        return active;
    }

    public static void setTheme(Theme theme) {
        if (theme == null || theme == active) return;
        active = theme;
        save();
        notifyListeners();
    }

    public static void applyFromConfig(String themeId) {
        Theme t = Themes.byId(themeId);
        boolean changed = t != active;
        active = t;
        if (changed) notifyListeners();
    }

    public static void addListener(Runnable r) {
        listeners.add(r);
    }

    public static void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private static void notifyListeners() {
        for (Runnable r : new ArrayList<>(listeners)) {
            try { r.run(); } catch (Exception e) { LOGGER.debug("theme listener failed", e); }
        }
    }

    public static void load() {
        Path file = filePath();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("theme")) active = Themes.byId(obj.get("theme").getAsString());
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("failed to load theme state", e);
        }
    }

    public static void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("theme", active.id());
        try {
            Files.writeString(filePath(), obj.toString());
        } catch (IOException e) {
            LOGGER.warn("failed to save theme state", e);
        }
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private ThemeState() {
    }
}
