package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.common.worlddata.BG2Data;
import com.direwolf20.buildinggadgets2.util.GadgetNBT;
import com.direwolf20.buildinggadgets2.util.context.ItemActionContext;
import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import java.util.ArrayList;
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
        if (!(context.player() instanceof ServerPlayer player)) return;
        ArrayList<StatePos> list = BG2Data.get(player.server.overworld())
                .getCopyPasteList(GadgetNBT.getUUID(gadget), false);
        if (list.isEmpty()) return;

        Map<BlockPos, CompoundTag> configs = BuildingGadgetsCompat.captureCopy(player,
                GadgetNBT.getCopyStartPos(gadget), GadgetNBT.getCopyEndPos(gadget));
        for (StatePos statePos : list) {
            CompoundTag config = configs.get(statePos.pos);
            if (config != null && !statePos.state.isAir()) {
                ((NodePayloadHolder) statePos).logisticsnetworks$setNode(config);
            }
        }
    }
}
