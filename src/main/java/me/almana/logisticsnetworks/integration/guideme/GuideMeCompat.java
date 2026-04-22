package me.almana.logisticsnetworks.integration.guideme;

import guideme.Guides;
import guideme.GuidesCommon;
import me.almana.logisticsnetworks.Logisticsnetworks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class GuideMeCompat {

    private static final String GUIDEME_MOD_ID = "guideme";
    private static final Identifier GUIDE_ID = Identifier.fromNamespaceAndPath(Logisticsnetworks.MOD_ID, "guide");
    private static Boolean loaded = null;

    private GuideMeCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(GUIDEME_MOD_ID);
        }
        return loaded;
    }

    public static ItemStack createGuideItem() {
        if (!isLoaded()) {
            return ItemStack.EMPTY;
        }
        return Guides.createGuideItem(GUIDE_ID);
    }

    public static void openGuide(Player player, Identifier guideId) {
        if (!isLoaded() || player == null) return;
        GuidesCommon.openGuide(player, guideId);
    }
}
