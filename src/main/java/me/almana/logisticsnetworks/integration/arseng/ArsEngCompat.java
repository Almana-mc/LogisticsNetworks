package me.almana.logisticsnetworks.integration.arseng;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import gripe._90.arseng.block.entity.MESourceJarBlockEntity;
import gripe._90.arseng.definition.ArsEngCapabilities;
import gripe._90.arseng.me.key.SourceKey;
import gripe._90.arseng.me.misc.GenericStackSourceStorage;
import gripe._90.arseng.part.SourceP2PTunnelPart;
import me.almana.logisticsnetworks.integration.ars.SourceTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

    @Nullable
    public static SourceTransferHelper.SourceEnd storageEnd(ISourceTile tile, ServerLevel level, BlockPos pos) {
        return isLoaded() ? Storage.end(tile, level, pos) : null;
    }

    public static boolean isTunnel(ISourceTile tile) {
        return isLoaded() && Storage.isTunnel(tile);
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

    private static final class Storage {
        @Nullable
        static SourceTransferHelper.SourceEnd end(ISourceTile tile, ServerLevel level, BlockPos pos) {
            if (!(tile instanceof GenericStackSourceStorage storage)) return null;
            return new InventoryEnd(storage.inv(), SourceTransferHelper.identity(level, pos, storage.inv()));
        }

        // P2P handlers misreport stored source
        static boolean isTunnel(ISourceTile tile) {
            return tile.getClass().getDeclaringClass() == SourceP2PTunnelPart.class;
        }
    }

    // per-slot calls, real amounts
    private record InventoryEnd(GenericInternalInventory inv, Object identity)
            implements SourceTransferHelper.SourceEnd {
        @Override
        public int extract(int amount, boolean simulate) {
            long moved = 0;
            for (int slot = 0; slot < inv.size() && moved < amount; slot++)
                moved += inv.extract(slot, SourceKey.KEY, amount - moved, Actionable.ofSimulate(simulate));
            return (int) moved;
        }

        @Override
        public int receive(int amount, boolean simulate) {
            long moved = 0;
            for (int slot = 0; slot < inv.size() && moved < amount; slot++)
                moved += inv.insert(slot, SourceKey.KEY, amount - moved, Actionable.ofSimulate(simulate));
            return (int) moved;
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
