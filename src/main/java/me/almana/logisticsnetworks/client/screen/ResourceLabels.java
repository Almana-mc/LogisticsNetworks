package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.ChannelTint;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.integration.ars.ArsCompat;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

final class ResourceLabels {

    private static final String KEY = "gui.logisticsnetworks.node.resources.";
    private static final String[] SUFFIX = {"", "k", "M", "B"};

    private ResourceLabels() {
    }

    static String summary(int types) {
        if (types == ChannelType.ALL) return tr("summary.all");
        if (types == 0) return tr("summary.none");
        return Component.translatable(KEY + "summary.some", Integer.bitCount(types), ChannelType.values().length)
                .getString();
    }

    static void dots(GuiGraphics g, int dx, int dy, int types, Theme t) {
        for (ChannelType type : ChannelType.values()) {
            g.fill(dx, dy, dx + 3, dy + 3, type.in(types) ? ChannelTint.hue(type) : t.borderStrong());
            dx += 4;
        }
    }

    static String typeName(ChannelType type) {
        return Component.translatable("gui.logisticsnetworks.channel_type." + type.name().toLowerCase(Locale.ROOT))
                .getString();
    }

    static String lockReason(ChannelType type) {
        if (type == ChannelType.ENERGY) return tr("locked.mounted");
        boolean modLoaded = type == ChannelType.CHEMICAL ? MekanismCompat.isLoaded() : ArsCompat.isLoaded();
        return tr((modLoaded ? "locked.upgrade." : "locked.mod.") + type.name().toLowerCase(Locale.ROOT));
    }

    static String tr(String key) {
        return Component.translatable(KEY + key).getString();
    }

    // Two significant digits, truncated
    static String amount(int value) {
        if (value < 1000) return String.valueOf(value);
        int tier = (int) Math.log10(value) / 3;
        return new BigDecimal(value).round(new MathContext(2, RoundingMode.DOWN)).movePointLeft(3 * tier)
                .stripTrailingZeros().toPlainString() + SUFFIX[tier];
    }
}
