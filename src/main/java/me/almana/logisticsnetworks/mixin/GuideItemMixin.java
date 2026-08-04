package me.almana.logisticsnetworks.mixin;

import guideme.internal.item.GuideItem;
//? if >=26
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuideItem.class)
public abstract class GuideItemMixin extends Item {

    private GuideItemMixin() {
        super(null);
    }

    //? if <26 {
    /*@Override
    public String getCreatorModId(ItemStack stack) {
        Identifier guideId = GuideItem.getGuideId(stack);
        return guideId == null ? super.getCreatorModId(stack) : guideId.getNamespace();
    }
    *///?} else {
    @Override
    public String getCreatorModId(HolderLookup.Provider registries, ItemStack stack) {
        Identifier guideId = GuideItem.getGuideId(stack);
        if (guideId != null) {
            return guideId.getNamespace();
        }
        return super.getCreatorModId(registries, stack);
    }
    //?}
}
