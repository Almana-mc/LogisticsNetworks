package me.almana.logisticsnetworks.client.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
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
        if (theme == null || theme == active) {
            return;
        }
        active = theme;
        save();
        notifyListeners();
    }

    public static void applyFromConfig(String themeId) {
        Theme theme = Themes.byId(themeId);
        boolean changed = theme != active;
        active = theme;
        if (changed) {
            notifyListeners();
        }
    }

    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public static void load() {
        Path file = filePath();
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file);
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            if (object.has("theme")) {
                active = Themes.byId(object.get("theme").getAsString());
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("failed to load theme state", exception);
        }
    }

    public static void save() {
        JsonObject object = new JsonObject();
        object.addProperty("theme", active.id());
        try {
            Files.writeString(filePath(), object.toString());
        } catch (IOException exception) {
            LOGGER.warn("failed to save theme state", exception);
        }
    }

    private static void notifyListeners() {
        for (Runnable listener : new ArrayList<>(listeners)) {
            try {
                listener.run();
            } catch (Exception exception) {
                LOGGER.debug("theme listener failed", exception);
            }
        }
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private ThemeState() {
    }
}
