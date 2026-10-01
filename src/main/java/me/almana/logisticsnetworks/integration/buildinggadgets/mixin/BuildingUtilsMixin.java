package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import com.llamalad7.mixinextras.sugar.Local;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodePayloadHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.util.BuildingUtils", remap = false)
abstract class BuildingUtilsMixin {
    @ModifyArg(method = {"build", "exchange"}, index = 1, at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/buildinggadgets2/common/events/ServerTickHandler;addToMap(Ljava/util/UUID;Lcom/direwolf20/buildinggadgets2/util/datatypes/StatePos;Lnet/minecraft/world/level/Level;BLnet/minecraft/world/entity/player/Player;ZZLnet/minecraft/world/item/ItemStack;Lcom/direwolf20/buildinggadgets2/common/events/ServerBuildList$BuildType;ZLnet/minecraft/core/BlockPos;)V"))
    private static StatePos logisticsnetworks$carryNode(StatePos queued, @Local StatePos source) {
        ((NodePayloadHolder) queued).logisticsnetworks$setNode(((NodePayloadHolder) source).logisticsnetworks$getNode());
        return queued;
    }
}
