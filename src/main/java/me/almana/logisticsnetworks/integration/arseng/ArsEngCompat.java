package me.almana.logisticsnetworks.integration.arseng;

import appeng.api.stacks.AEKey;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import gripe._90.arseng.definition.ArsEngCapabilities;
import gripe._90.arseng.me.key.SourceKey;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class ArsEngCompat {

    private static Boolean loaded = null;

    private ArsEngCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("arseng");
        }
        return loaded;
    }

    @Nullable
    public static AEKey sourceKey() {
        return isLoaded() ? Key.SOURCE : null;
    }

    @Nullable
    public static ISourceTile sourceTile(BlockEntity blockEntity, @Nullable Direction side) {
        return isLoaded() ? Caps.sourceTile(blockEntity, side) : null;
    }

    // defers loading arseng classes
    private static final class Key {
        static final AEKey SOURCE = SourceKey.KEY;
    }

    private static final class Caps {
        static ISourceTile sourceTile(BlockEntity blockEntity, @Nullable Direction side) {
            return blockEntity.getCapability(ArsEngCapabilities.SOURCE_TILE, side).orElse(null);
        }
    }
}
