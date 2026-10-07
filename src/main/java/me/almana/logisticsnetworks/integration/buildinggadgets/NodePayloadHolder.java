package me.almana.logisticsnetworks.integration.buildinggadgets;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public interface NodePayloadHolder {
    @Nullable
    CompoundTag logisticsnetworks$getNode();

    void logisticsnetworks$setNode(@Nullable CompoundTag payload);
}
