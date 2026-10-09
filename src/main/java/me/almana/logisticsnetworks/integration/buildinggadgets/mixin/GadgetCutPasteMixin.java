package me.almana.logisticsnetworks.integration.buildinggadgets.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.almana.logisticsnetworks.integration.buildinggadgets.BuildingGadgetsCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets2.common.items.GadgetCutPaste", remap = false)
abstract class GadgetCutPasteMixin {
    @WrapMethod(method = "cutAndStore")
    private void logisticsnetworks$guardCutProbe(Player player, ItemStack gadget, Operation<Void> original) {
        BuildingGadgetsCompat.setCutProbe(true);
        try {
            original.call(player, gadget);
        } finally {
            BuildingGadgetsCompat.setCutProbe(false);
        }
    }
}
