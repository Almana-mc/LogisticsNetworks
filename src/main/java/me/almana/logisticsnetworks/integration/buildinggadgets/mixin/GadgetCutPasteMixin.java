package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.almana.logisticsnetworks.integration.buildinggadgets.BuildingGadgetsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.common.items.GadgetCutPaste", remap = false)
abstract class GadgetCutPasteMixin {
    // BG2 1.0.8 fires BreakEvent here
    @WrapMethod(method = "customCutValidation")
    private static boolean logisticsnetworks$guardCutProbe(BlockState state, Level level, Player player, BlockPos pos,
            Operation<Boolean> original) {
        BuildingGadgetsCompat.setCutProbe(true);
        try {
            return original.call(state, level, player, pos);
        } finally {
            BuildingGadgetsCompat.setCutProbe(false);
        }
    }
}
