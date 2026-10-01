package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.common.worlddata.BG2Data;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.context.ItemActionContext;
import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import java.util.Map;
import me.almana.logisticsnetworks.integration.buildinggadgets.BuildingGadgetsCompat;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodePayloadHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.common.items.GadgetCopyPaste", remap = false)
abstract class GadgetCopyPasteMixin {
    @Inject(method = "buildAndStore", at = @At("RETURN"))
    private void logisticsnetworks$copyNodes(ItemActionContext context, ItemStack gadget, CallbackInfo ci) {
        BlockPos start = GadgetNBT.getCopyStartPos(gadget);
        BlockPos end = GadgetNBT.getCopyEndPos(gadget);
        if (!(context.player() instanceof ServerPlayer player)
                || start.equals(GadgetNBT.nullPos) || end.equals(GadgetNBT.nullPos)) return;

        Map<BlockPos, CompoundTag> configs = BuildingGadgetsCompat.captureCopy(player, start, end);
        if (configs.isEmpty()) return;
        for (StatePos statePos : BG2Data.get(player.server.overworld()).getCopyPasteList(GadgetNBT.getUUID(gadget), false)) {
            CompoundTag config = configs.get(statePos.pos);
            if (config != null) ((NodePayloadHolder) statePos).logisticsnetworks$setNode(config);
        }
    }
}
