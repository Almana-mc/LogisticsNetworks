package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.networking.IInWorldGridNodeHost;
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

public final class AE2StorageAdapter implements StorageAdapter {

    public static final AE2StorageAdapter INSTANCE = new AE2StorageAdapter();

    private AE2StorageAdapter() {
    }

    @Override
    public StorageBackend backend() {
        return StorageBackend.AE2;
    }

    @Override
    public boolean detects(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof IInWorldGridNodeHost;
    }

    @Nullable
    @Override
    public StorageAccess resolve(ServerLevel level, StorageLink link) {
        return AE2StorageAccess.resolve(level, link);
    }

    @Override
    public boolean isPattern(ItemStack stack, Level level) {
        return AE2PatternReader.isPattern(stack);
    }

    @Override
    public List<LinkedStorage.PatternEntry> readPatternInputs(ItemStack stack, Level level) {
        return AE2PatternReader.readInputs(stack);
    }

    @Override
    public List<LinkedStorage.PatternEntry> readPatternOutputs(ItemStack stack, Level level) {
        return AE2PatternReader.readOutputs(stack);
    }

    @Override
    public void registerWrenchLinkable() {
        AE2StorageAccess.registerWrenchLinkable();
    }
}
