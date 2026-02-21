package me.almana.logisticsnetworks.integration.mekanism;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MekanismCompat {

    private static final String MEKANISM_MOD_ID = "mekanism";
    private static Boolean loaded = null;

    private MekanismCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MEKANISM_MOD_ID);
        }
        return loaded;
    }

    public static boolean hasChemicalStorage(ServerLevel level, BlockPos pos) {
        if (!isLoaded())
            return false;
        return ChemicalTransferHelper.hasHandler(level, pos);
    }

    public static List<String> getBlacklistedChemicalNames(ServerLevel level, BlockPos pos) {
        if (!isLoaded())
            return Collections.emptyList();
        return ChemicalTransferHelper.getBlacklistedChemicalNames(level, pos);
    }

    public static boolean chemicalHasTag(String chemicalId, String tagId) {
        if (!isLoaded())
            return false;
        return ChemicalTransferHelper.chemicalHasTag(chemicalId, tagId);
    }

    @Nullable
    public static String getChemicalIdFromItem(ItemStack itemStack) {
        if (!isLoaded())
            return null;
        return ChemicalTransferHelper.getChemicalIdFromItem(itemStack);
    }

    @Nullable
    public static List<String> getChemicalTagsFromItem(ItemStack itemStack) {
        if (!isLoaded())
            return null;
        return ChemicalTransferHelper.getChemicalTagsFromItem(itemStack);
    }

    @Nullable
    public static ResourceLocation getChemicalIcon(String chemicalId) {
        if (!isLoaded())
            return null;
        return ChemicalTransferHelper.getChemicalIcon(chemicalId);
    }

    public static int getChemicalTint(String chemicalId) {
        if (!isLoaded())
            return 0xFFFFFFFF;
        return ChemicalTransferHelper.getChemicalTint(chemicalId);
    }

    @Nullable
    public static Component getChemicalTextComponent(String chemicalId) {
        if (!isLoaded())
            return null;
        return ChemicalTransferHelper.getChemicalTextComponent(chemicalId);
    }
}
