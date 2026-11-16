package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.common.worlddata.BG2Data;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.datatypes.TagPos;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import me.almana.logisticsnetworks.integration.buildinggadgets.BuildingGadgetsCompat;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodeTransit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    // New cut replaces pending nodes
    @Inject(method = "cutAndStore", at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/buildinggadgets2/common/worlddata/BG2Data;addToTEMap(Ljava/util/UUID;Ljava/util/ArrayList;)V"))
    private void logisticsnetworks$dropPendingNodes(Player player, ItemStack gadget, CallbackInfo ci,
            @Local BG2Data data) {
        if (!(player.level() instanceof ServerLevel level)) return;
        ArrayList<TagPos> pending = data.peekTEMap(GadgetNBT.getUUID(gadget));
        if (pending == null) return;
        for (TagPos tagPos : pending) {
            if (!tagPos.tag.contains(NodeTransit.KEY_NODE)) continue;
            BuildingGadgetsCompat.dropMoved(level, player.blockPosition(),
                    tagPos.tag.getCompound(NodeTransit.KEY_NODE));
            // Strip so nothing drops twice
            tagPos.tag.remove(NodeTransit.KEY_NODE);
        }
    }
}
