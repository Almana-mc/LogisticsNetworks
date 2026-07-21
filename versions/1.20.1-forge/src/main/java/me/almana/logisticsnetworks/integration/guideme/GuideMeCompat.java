package me.almana.logisticsnetworks.integration.guideme;

import guideme.GuidesCommon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

public final class GuideMeCompat {

    private static final String GUIDEME_MOD_ID = "guideme";
    private static Boolean loaded = null;

    private GuideMeCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(GUIDEME_MOD_ID);
        }
        return loaded;
    }

    public static void openGuide(Player player, ResourceLocation guideId) {
        if (!isLoaded() || player == null) return;
        GuidesCommon.openGuide(player, guideId);
    }
}
