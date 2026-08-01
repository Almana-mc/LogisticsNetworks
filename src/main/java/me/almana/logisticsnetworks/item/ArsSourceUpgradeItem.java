package me.almana.logisticsnetworks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=26 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
//? if <26 {
/*import java.util.List;
*///?}
//? if forge {
/*import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
*///?}

import java.util.function.Consumer;

public class ArsSourceUpgradeItem extends Item {

    public ArsSourceUpgradeItem(Properties properties) {
        super(properties);
    }

    //? if forge {
    /*@Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        appendLines(tooltip::add);
    }
    *///?}

    //? if >=1.21 && <26 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        appendLines(tooltip::add);
    }
    *///?}

    //? if >=26 {
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        appendLines(tooltip);
    }
    //?}

    private static void appendLines(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.logisticsnetworks.ars_source_upgrade")
                .withStyle(ChatFormatting.GRAY));
    }
}
