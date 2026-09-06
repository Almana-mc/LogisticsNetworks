// Enable when 26.1.2 is supported.
/*
package me.almana.logisticsnetworks.integration.create.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.integration.create.CreateNodeAttachment;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
abstract class ContraptionInteractionMixin {
    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void logisticsnetworks$interact(Player player, BlockPos localPos, Direction side,
            InteractionHand hand, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof WrenchItem wrench)) {
            return;
        }
        LogisticsNodeEntity node = CreateNodeAttachment.findNode(
                (AbstractContraptionEntity) (Object) this, localPos);
        if (node == null) {
            return;
        }
        if (player.level().isClientSide()) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(wrench.interactWithMountedNode(node, player, stack).consumesAction());
    }
}
*/
