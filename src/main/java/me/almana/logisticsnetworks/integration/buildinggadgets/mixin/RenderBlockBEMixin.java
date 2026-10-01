package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import me.almana.logisticsnetworks.integration.buildinggadgets.BuildingGadgetsCompat;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodePayloadHolder;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodeTransit;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.common.blockentities.RenderBlockBE", remap = false)
abstract class RenderBlockBEMixin implements NodePayloadHolder {
    @Shadow
    public CompoundTag blockEntityData;

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

    @Inject(method = "setRealBlock", at = @At("HEAD"))
    private void logisticsnetworks$takeMovedNode(BlockState realBlock, CallbackInfo ci) {
        if (blockEntityData == null || !blockEntityData.contains(NodeTransit.KEY_NODE)) return;
        BlockEntity self = (BlockEntity) (Object) this;
        if (logisticsnetworks$node != null && self.getLevel() instanceof ServerLevel level) {
            BuildingGadgetsCompat.release(level, self.getBlockPos(), logisticsnetworks$node);
        }
        CompoundTag data = blockEntityData.copy();
        logisticsnetworks$node = data.getCompound(NodeTransit.KEY_NODE);
        data.remove(NodeTransit.KEY_NODE);
        blockEntityData = data.isEmpty() ? null : data;
    }

    @Inject(method = "setRealBlock", at = @At("RETURN"))
    private void logisticsnetworks$spawnNode(BlockState realBlock, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (logisticsnetworks$node == null || !(self.getLevel() instanceof ServerLevel level)) return;
        CompoundTag payload = logisticsnetworks$node;
        logisticsnetworks$node = null;
        BuildingGadgetsCompat.spawn(level, self.getBlockPos(), payload);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void logisticsnetworks$saveNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        if (logisticsnetworks$node != null) tag.put(NodeTransit.KEY_NODE, logisticsnetworks$node);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void logisticsnetworks$loadNode(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        logisticsnetworks$node = tag.contains(NodeTransit.KEY_NODE) ? tag.getCompound(NodeTransit.KEY_NODE) : null;
    }
}
