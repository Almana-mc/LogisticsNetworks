package me.almana.logisticsnetworks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class MekanismChemicalUpgradeItem extends Item {

    public MekanismChemicalUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.logisticsnetworks.mekanism_chemical_upgrade")
                .withStyle(ChatFormatting.GRAY));
    }
}
