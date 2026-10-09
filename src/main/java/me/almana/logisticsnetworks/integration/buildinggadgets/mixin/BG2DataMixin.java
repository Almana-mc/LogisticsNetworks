package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodePayloadHolder;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodeTransit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.common.worlddata.BG2Data", remap = false)
abstract class BG2DataMixin {
    @Inject(method = "statePosListToNBTMapArray", at = @At("RETURN"))
    private static void logisticsnetworks$writeNodes(ArrayList<StatePos> list,
            CallbackInfoReturnable<CompoundTag> cir) {
        if (list == null) return;
        Map<Long, CompoundTag> nodes = new HashMap<>();
        for (StatePos statePos : list) {
            CompoundTag node = ((NodePayloadHolder) statePos).logisticsnetworks$getNode();
            if (node != null) nodes.put(statePos.pos.asLong(), node);
        }
        if (!nodes.isEmpty()) cir.getReturnValue().put(NodeTransit.KEY_NODES, NodeTransit.writeNodes(nodes));
    }

    @Inject(method = "statePosListFromNBTMapArray", at = @At("RETURN"))
    private static void logisticsnetworks$readNodes(CompoundTag tag,
            CallbackInfoReturnable<ArrayList<StatePos>> cir) {
        ListTag list = tag.getListOrEmpty(NodeTransit.KEY_NODES);
        if (list.isEmpty()) return;
        Map<Long, CompoundTag> nodes = NodeTransit.readNodes(list);
        for (StatePos statePos : cir.getReturnValue()) {
            CompoundTag node = nodes.get(statePos.pos.asLong());
            if (node != null) ((NodePayloadHolder) statePos).logisticsnetworks$setNode(node);
        }
    }
}
