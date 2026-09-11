package me.almana.logisticsnetworks.integration.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface StorageAdapter {

    StorageBackend backend();

    boolean detects(Level level, BlockPos pos);

    @Nullable
    StorageAccess resolve(ServerLevel level, StorageLink link);

    boolean isPattern(ItemStack stack, Level level);

    List<LinkedStorage.PatternEntry> readPatternInputs(ItemStack stack, Level level);

    List<LinkedStorage.PatternEntry> readPatternOutputs(ItemStack stack, Level level);

    void registerWrenchLinkable();
}
