package me.almana.logisticsnetworks.integration.functionalstorage.mixin;

import com.buuz135.functionalstorage.inventory.BigInventoryHandler;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.buuz135.functionalstorage.inventory.BigInventoryHandler", remap = false)
public abstract class DrawerInsertionMixin {
    @Redirect(method = "insertItem", at = @At(value = "INVOKE",
            target = "Lcom/buuz135/functionalstorage/inventory/BigInventoryHandler;getSlotLimit(I)I"), require = 0)
    private int logisticsnetworks$insertionLimit(BigInventoryHandler handler, int slot,
            int insertionSlot, ItemStack stack, boolean simulate) {
        int limit = handler.getSlotLimit(slot);
        if (!handler.isCreative() && handler.getStoredStacks().get(slot).getStack().isEmpty()) {
            return (int) (limit * (stack.getMaxStackSize() / 64.0));
        }
        return limit;
    }
}
