package me.almana.logisticsnetworks.integration.refinedstorage;

import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import me.almana.logisticsnetworks.integration.storage.StorageAccess;
import me.almana.logisticsnetworks.integration.storage.StorageAdapter;
import me.almana.logisticsnetworks.integration.storage.StorageBackend;
import me.almana.logisticsnetworks.integration.storage.StorageLink;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class RefinedStorageAdapter implements StorageAdapter {

    public static final RefinedStorageAdapter INSTANCE = new RefinedStorageAdapter();

    private RefinedStorageAdapter() {
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.REFINED_STORAGE;
    }

    @Override
    public boolean detects(Level level, BlockPos pos) {
        return RefinedStorageAccess.findNode(level, pos) != null;
    }

    @Nullable
    @Override
    public StorageAccess resolve(ServerLevel level, StorageLink link) {
        return RefinedStorageAccess.resolve(level, link);
    }

    @Override
    public boolean isPattern(ItemStack stack, Level level) {
        return RefinedStoragePatternReader.isPattern(stack, level);
    }

    @Override
    public List<LinkedStorage.PatternEntry> readPatternInputs(ItemStack stack, Level level) {
        return RefinedStoragePatternReader.readInputs(stack, level);
    }

    @Override
    public List<LinkedStorage.PatternEntry> readPatternOutputs(ItemStack stack, Level level) {
        return RefinedStoragePatternReader.readOutputs(stack, level);
    }

    @Override
    public void registerWrenchLinkable() {
    }
}
