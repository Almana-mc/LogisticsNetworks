package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import me.almana.logisticsnetworks.integration.buildinggadgets.NodePayloadHolder;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.util.datatypes.StatePos", remap = false)
abstract class StatePosMixin implements NodePayloadHolder {
    @Unique
    @Nullable
    private CompoundTag logisticsnetworks$node;

    @Override
    public CompoundTag logisticsnetworks$getNode() {
        return logisticsnetworks$node;
    }

    @Override
    public void logisticsnetworks$setNode(CompoundTag payload) {
        logisticsnetworks$node = payload;
    }
}
