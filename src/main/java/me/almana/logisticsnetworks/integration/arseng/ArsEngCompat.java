package me.almana.logisticsnetworks.integration.arseng;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import gripe._90.arseng.block.entity.MESourceJarBlockEntity;
import gripe._90.arseng.definition.ArsEngCapabilities;
import gripe._90.arseng.me.key.SourceKey;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
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

    @Nullable
    public static SourceTransferHelper.SourceEnd jarEnd(@Nullable BlockEntity blockEntity) {
        return isLoaded() ? Jar.end(blockEntity) : null;
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

    private static final class Jar {
        @Nullable
        static SourceTransferHelper.SourceEnd end(@Nullable BlockEntity blockEntity) {
            return blockEntity instanceof MESourceJarBlockEntity jar ? new JarEnd(jar) : null;
        }
    }

    // powered grid calls, real amounts
    private record JarEnd(MESourceJarBlockEntity jar) implements SourceTransferHelper.SourceEnd {
        @Override
        public int extract(int amount, boolean simulate) {
            IGrid grid = activeGrid();
            if (grid == null || amount <= 0) return 0;
            return (int) StorageHelper.poweredExtraction(grid.getEnergyService(), grid.getStorageService().getInventory(),
                    SourceKey.KEY, amount, IActionSource.ofMachine(jar), Actionable.ofSimulate(simulate));
        }

        @Override
        public int receive(int amount, boolean simulate) {
            IGrid grid = activeGrid();
            if (grid == null || amount <= 0) return 0;
            return (int) StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                    SourceKey.KEY, amount, IActionSource.ofMachine(jar), Actionable.ofSimulate(simulate));
        }

        @Override
        public Object identity() {
            IGrid grid = jar.getMainNode().getGrid();
            return grid != null ? grid : jar;
        }

        @Nullable
        private IGrid activeGrid() {
            return jar.getMainNode().isActive() ? jar.getMainNode().getGrid() : null;
        }
    }
}
