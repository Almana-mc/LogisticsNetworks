package me.almana.logisticsnetworks.filter;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public final class FilterTagUtil {

    private FilterTagUtil() {
    }

    @Nullable
    public static String normalizeTag(@Nullable String tagValue) {
        if (tagValue == null) {
            return null;
        }

        String cleaned = tagValue.trim();
        while (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (cleaned.isEmpty()) {
            return null;
        }

        Identifier id = Identifier.tryParse(cleaned);
        return id == null ? null : id.toString();
    }
}
