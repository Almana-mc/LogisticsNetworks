package me.almana.logisticsnetworks.mixin;

import guideme.internal.item.GuideItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuideItem.class)
public abstract class GuideItemMixin extends Item {

    private GuideItemMixin() {
        super(null);
    }

    @Override
    public String getCreatorModId(ItemStack stack) {
        ResourceLocation guideId = GuideItem.getGuideId(stack);
        return guideId == null ? super.getCreatorModId(stack) : guideId.getNamespace();
    }
}
