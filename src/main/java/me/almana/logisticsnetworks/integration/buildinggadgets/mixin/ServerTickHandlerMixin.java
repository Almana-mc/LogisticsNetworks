package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.common.events.ServerBuildList;
import com.direwolf20.buildinggadgets2.util.datatypes.TagPos;
import com.llamalad7.mixinextras.sugar.Local;
import me.almana.logisticsnetworks.integration.buildinggadgets.BuildingGadgetsCompat;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodeTransit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.common.events.ServerTickHandler", remap = false)
abstract class ServerTickHandlerMixin {
    @Inject(method = "cut", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;removeBlockEntity(Lnet/minecraft/core/BlockPos;)V"))
    private static void logisticsnetworks$captureNode(ServerBuildList list, Player player, CallbackInfo ci,
            @Local BlockPos blockPos) {
        if (!(list.level instanceof ServerLevel level)) return;
        CompoundTag payload = BuildingGadgetsCompat.captureMove(level, blockPos);
        if (payload == null) return;

        BlockPos relative = blockPos.subtract(list.cutStart);
        TagPos holder = list.teData.stream().filter(tagPos -> tagPos.pos.equals(relative)).findFirst().orElse(null);
        if (holder == null) {
            holder = new TagPos(new CompoundTag(), relative);
            list.teData.add(holder);
        }
        holder.tag.put(NodeTransit.KEY_NODE, payload);
    }

    @Inject(method = "removeEmptyLists", at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/buildinggadgets2/common/worlddata/BG2Data;getTEMap(Ljava/util/UUID;)Ljava/util/ArrayList;"))
    private static void logisticsnetworks$dropUnpastedNodes(ServerTickEvent.Pre event, CallbackInfo ci,
            @Local ServerBuildList list) {
        if (!(list.level instanceof ServerLevel level)) return;
        for (TagPos tagPos : list.teData) {
            if (tagPos.tag.contains(NodeTransit.KEY_NODE)) {
                BuildingGadgetsCompat.dropMoved(level, tagPos.pos.offset(list.lookingAt),
                        tagPos.tag.getCompound(NodeTransit.KEY_NODE));
            }
        }
    }
}
