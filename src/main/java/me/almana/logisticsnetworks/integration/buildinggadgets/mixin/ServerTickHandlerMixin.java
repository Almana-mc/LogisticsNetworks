package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.common.blockentities.RenderBlockBE;
import com.direwolf20.buildinggadgets2.common.events.ServerBuildList;
import com.direwolf20.buildinggadgets2.util.BuildingUtils;
import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import com.direwolf20.buildinggadgets2.util.datatypes.TagPos;
import com.llamalad7.mixinextras.sugar.Local;
import me.almana.logisticsnetworks.integration.buildinggadgets.BuildingGadgetsCompat;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodePayloadHolder;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodeTransit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.objectweb.asm.Opcodes;
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
            @Local ServerBuildList list, @Local Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (TagPos tagPos : list.teData) {
            if (tagPos.tag.contains(NodeTransit.KEY_NODE)) {
                BuildingGadgetsCompat.dropMoved(level, player.blockPosition(), tagPos.tag.getCompound(NodeTransit.KEY_NODE));
            }
        }
    }

    @Inject(method = "remove", at = @At(value = "FIELD",
            target = "Lcom/direwolf20/buildinggadgets2/common/blockentities/RenderBlockBE;renderBlock:Lnet/minecraft/world/level/block/state/BlockState;",
            opcode = Opcodes.GETFIELD))
    private static void logisticsnetworks$releaseNode(ServerBuildList list, Player player, CallbackInfo ci,
            @Local RenderBlockBE renderBlockBE) {
        if (!(list.level instanceof ServerLevel level)) return;
        BlockPos pos = renderBlockBE.getBlockPos();
        NodePayloadHolder holder = (NodePayloadHolder) renderBlockBE;
        CompoundTag held = holder.logisticsnetworks$getNode();
        if (held != null) {
            holder.logisticsnetworks$setNode(null);
            BuildingGadgetsCompat.release(level, pos, held);
        }
        CompoundTag data = renderBlockBE.blockEntityData;
        if (data != null && data.contains(NodeTransit.KEY_NODE)) {
            BuildingGadgetsCompat.release(level, pos, data.getCompound(NodeTransit.KEY_NODE));
        }
    }

    @Inject(method = {"build", "exchange"}, require = 3, at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/buildinggadgets2/common/blockentities/RenderBlockBE;setRenderData(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;B)V",
            shift = At.Shift.AFTER))
    private static void logisticsnetworks$chargeNode(ServerBuildList list, Player player, CallbackInfo ci,
            @Local StatePos statePos, @Local RenderBlockBE be) {
        CompoundTag config = ((NodePayloadHolder) statePos).logisticsnetworks$getNode();
        NodePayloadHolder holder = (NodePayloadHolder) be;
        if (config == null || holder.logisticsnetworks$getNode() != null
                || be.blockEntityData != null && be.blockEntityData.contains(NodeTransit.KEY_NODE)
                || !(player instanceof ServerPlayer serverPlayer)) return;
        holder.logisticsnetworks$setNode(BuildingGadgetsCompat.chargeCopy(serverPlayer, config, list,
                cost -> BuildingUtils.removeStacksFromInventory(player, cost, true, list.boundPos, list.getDirection())
                        && BuildingUtils.removeStacksFromInventory(player, cost, false, list.boundPos,
                                list.getDirection())));
    }
}
