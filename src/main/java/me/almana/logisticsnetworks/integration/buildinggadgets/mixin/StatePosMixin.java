package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import com.direwolf20.buildinggadgets2.util.datatypes.TagPos;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodePayloadHolder;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodeTransit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.util.datatypes.StatePos", remap = false)
abstract class StatePosMixin implements NodePayloadHolder {
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

    @Inject(method = "rotate90Degrees", at = @At("HEAD"))
    private static void logisticsnetworks$liftMoves(ArrayList<StatePos> list, ArrayList<TagPos> tags,
            CallbackInfoReturnable<ArrayList<StatePos>> cir, @Share("moved") LocalRef<Map<BlockPos, CompoundTag>> moved) {
        if (list == null || list.isEmpty()) return;
        Map<BlockPos, CompoundTag> lifted = new HashMap<>();
        moved.set(lifted);
        if (tags == null) return;
        for (ListIterator<TagPos> it = tags.listIterator(); it.hasNext(); ) {
            TagPos entry = it.next();
            if (!entry.tag.contains(NodeTransit.KEY_NODE)) continue;
            lifted.put(entry.pos, entry.tag.getCompound(NodeTransit.KEY_NODE));
            CompoundTag rest = entry.tag.copy();
            rest.remove(NodeTransit.KEY_NODE);
            if (rest.isEmpty()) it.remove();
            else it.set(new TagPos(rest, entry.pos));
        }
    }

    @Inject(method = "rotate90Degrees", at = @At("RETURN"))
    private static void logisticsnetworks$rotateNodes(ArrayList<StatePos> list, ArrayList<TagPos> tags,
            CallbackInfoReturnable<ArrayList<StatePos>> cir, @Share("moved") LocalRef<Map<BlockPos, CompoundTag>> moved) {
        Map<BlockPos, CompoundTag> lifted = moved.get();
        if (lifted == null) return;
        ArrayList<StatePos> rotated = cir.getReturnValue();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag config = ((NodePayloadHolder) list.get(i)).logisticsnetworks$getNode();
            if (config != null) ((NodePayloadHolder) rotated.get(i)).logisticsnetworks$setNode(NodeTransit.rotateCopy(config));
        }
        if (lifted.isEmpty()) return;
        Map<BlockPos, CompoundTag> placed = new HashMap<>();
        lifted.forEach((pos, payload) -> placed.put(new BlockPos(-pos.getZ(), pos.getY(), pos.getX()),
                NodeTransit.rotateMove(payload)));
        for (ListIterator<TagPos> it = tags.listIterator(); it.hasNext(); ) {
            TagPos entry = it.next();
            CompoundTag payload = placed.remove(entry.pos);
            if (payload == null) continue;
            CompoundTag tag = entry.tag.copy();
            tag.put(NodeTransit.KEY_NODE, payload);
            it.set(new TagPos(tag, entry.pos));
        }
        placed.forEach((pos, payload) -> {
            CompoundTag tag = new CompoundTag();
            tag.put(NodeTransit.KEY_NODE, payload);
            tags.add(new TagPos(tag, pos));
        });
    }
}
