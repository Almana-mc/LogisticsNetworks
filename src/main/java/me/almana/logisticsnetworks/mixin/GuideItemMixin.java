package me.almana.logisticsnetworks.mixin;

import guideme.internal.item.GuideItem;
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

    @Override
    public String getCreatorModId(HolderLookup.Provider registries, ItemStack stack) {
        Identifier guideId = GuideItem.getGuideId(stack);
        if (guideId != null) {
            return guideId.getNamespace();
        }
        return super.getCreatorModId(registries, stack);
    }
}
