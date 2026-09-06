// Enable when 26.1.2 is supported.
/*
package me.almana.logisticsnetworks.integration.create.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import me.almana.logisticsnetworks.integration.create.CreateNodeAttachment;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption", remap = false)
abstract class ContraptionLifecycleMixin {
    @Inject(method = "onEntityCreated", at = @At("TAIL"), remap = false)
    private void logisticsnetworks$bindNodes(AbstractContraptionEntity entity, CallbackInfo ci) {
        CreateNodeAttachment.bindNodes((Contraption) (Object) this, entity);
    }

    @Inject(method = "addBlocksToWorld", at = @At("TAIL"), remap = false)
    private void logisticsnetworks$dismountNodes(Level level, StructureTransform transform, CallbackInfo ci) {
        CreateNodeAttachment.dismountNodes((Contraption) (Object) this, level, transform);
    }
}
*/
