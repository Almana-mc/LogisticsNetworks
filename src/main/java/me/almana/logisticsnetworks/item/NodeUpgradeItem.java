package me.almana.logisticsnetworks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class NodeUpgradeItem extends Item {

        private final int itemCap;
        private final int fluidCapMb;
        private final int energyCap;
        private final int minDelay;

        public NodeUpgradeItem(Properties properties, int itemCap, int fluidCapMb, int energyCap, int minDelay) {
                super(properties);
                this.itemCap = itemCap;
                this.fluidCapMb = fluidCapMb;
                this.energyCap = energyCap;
                this.minDelay = minDelay;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                        TooltipFlag flag) {
                tooltip.accept(Component.translatable("tooltip.logisticsnetworks.upgrade.description")
                                .withStyle(ChatFormatting.GRAY));

                addStatTooltip(tooltip, "items", formatCap(itemCap));
                addStatTooltip(tooltip, "fluids", String.format("%,d", fluidCapMb));
                addStatTooltip(tooltip, "chemicals", String.format("%,d", fluidCapMb));
                addStatTooltip(tooltip, "energy", formatCap(energyCap));
                addStatTooltip(tooltip, "source", String.format("%,d", fluidCapMb));
                addStatTooltip(tooltip, "delay", String.valueOf(minDelay));
        }

        private void addStatTooltip(Consumer<Component> tooltip, String statKey, String value) {
                tooltip.accept(Component.translatable("tooltip.logisticsnetworks.upgrade." + statKey, value)
                                .withStyle(ChatFormatting.DARK_GRAY));
        }

        private String formatCap(int cap) {
                return (cap == Integer.MAX_VALUE)
                                ? Component.translatable("tooltip.logisticsnetworks.upgrade.unlimited").getString()
                                : String.format("%,d", cap);
        }
}
