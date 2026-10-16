package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NodeUpgradeItem extends Item {

        public NodeUpgradeItem(Properties properties) {
                super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                        TooltipFlag flag) {
                tooltip.add(Component.translatable("tooltip.logisticsnetworks.upgrade.description")
                                .withStyle(ChatFormatting.GRAY));

                // Values from upgrades.json
                int tier = NodeUpgradeData.getUpgradeTier(stack);
                addStatTooltip(tooltip, "items", formatCap(NodeUpgradeData.getItemOperationCap(tier)));
                addStatTooltip(tooltip, "fluids", formatCap(NodeUpgradeData.getFluidOperationCapMb(tier)));
                addStatTooltip(tooltip, "chemicals", formatCap(NodeUpgradeData.getChemicalOperationCap(tier)));
                addStatTooltip(tooltip, "energy", formatCap(NodeUpgradeData.getEnergyOperationCap(tier)));
                addStatTooltip(tooltip, "source", formatCap(NodeUpgradeData.getSourceOperationCap(tier)));
                addStatTooltip(tooltip, "delay", String.valueOf(NodeUpgradeData.getMinTickDelay(tier)));
        }

        private void addStatTooltip(List<Component> tooltip, String statKey, String value) {
                tooltip.add(Component.translatable("tooltip.logisticsnetworks.upgrade." + statKey, value)
                                .withStyle(ChatFormatting.DARK_GRAY));
        }

        private String formatCap(int cap) {
                return (cap == Integer.MAX_VALUE)
                                ? Component.translatable("tooltip.logisticsnetworks.upgrade.unlimited").getString()
                                : String.format("%,d", cap);
        }
}
