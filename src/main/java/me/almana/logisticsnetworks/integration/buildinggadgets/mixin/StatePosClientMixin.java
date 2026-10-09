package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.direwolf20.buildinggadgets2.util.ItemStackKey;
import com.direwolf20.buildinggadgets2.util.datatypes.StatePos;
import java.util.ArrayList;
import java.util.Map;
import me.almana.logisticsnetworks.integration.buildinggadgets.BuildingGadgetsCompat;
import me.almana.logisticsnetworks.integration.buildinggadgets.NodePayloadHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.util.datatypes.StatePos", remap = false)
abstract class StatePosClientMixin {
    @Inject(method = "getItemList", at = @At("RETURN"))
    private static void logisticsnetworks$addNodeCosts(ArrayList<StatePos> list, Level level, Player player,
            CallbackInfoReturnable<Map<ItemStackKey, Integer>> cir) {
        if (list == null || level == null) return;
        for (StatePos statePos : list) {
            CompoundTag config = ((NodePayloadHolder) statePos).logisticsnetworks$getNode();
            if (config == null || statePos.state.isAir()) continue;
            for (ItemStack stack : BuildingGadgetsCompat.pasteCost(config, level.registryAccess())) {
                cir.getReturnValue().merge(new ItemStackKey(stack, true), stack.getCount(), Integer::sum);
            }
        }
    }
}
