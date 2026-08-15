package me.almana.logisticsnetworks.integration.sable.mixin;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.SimAssemblyHelper")
public abstract class SimulatedDisassemblyMixin {

    @Redirect(method = "disassembleSubLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setPos(Lnet/minecraft/world/phys/Vec3;)V"))
    private static void logisticsnetworks$moveNodeAttachment(Entity entity, Vec3 position) {
        if (entity instanceof LogisticsNodeEntity node) {
            node.moveAttachment(BlockPos.containing(position), position);
        } else {
            entity.setPos(position);
        }
    }
}
