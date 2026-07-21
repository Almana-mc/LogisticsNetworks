package me.almana.logisticsnetworks.item;

import me.almana.logisticsnetworks.filter.NbtFilterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class NbtFilterItem extends Item {

        public NbtFilterItem(Properties properties) {
                super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                ItemStack stack = player.getItemInHand(hand);
                return InteractionResultHolder.pass(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                        TooltipFlag flag) {
                tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.deprecated")
                                .withStyle(ChatFormatting.RED));

                boolean blacklist = NbtFilterData.isBlacklist(stack);
                List<NbtFilterData.NbtRule> rules = NbtFilterData.getRules(stack);
                long enabledRules = rules.stream().filter(NbtFilterData.NbtRule::enabled).count();
                String selection = rules.isEmpty()
                                ? Component.translatable("tooltip.logisticsnetworks.filter.nbt.none").getString()
                                : rules.get(0).path() + (rules.size() > 1 ? " +" + (rules.size() - 1) : "");

                tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.nbt.desc")
                                .withStyle(ChatFormatting.GRAY));

                tooltip.add(Component.translatable(
                                blacklist ? "tooltip.logisticsnetworks.filter.mode.blacklist"
                                                : "tooltip.logisticsnetworks.filter.mode.whitelist")
                                .withStyle(ChatFormatting.GRAY));

                tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.nbt", selection)
                                .withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable("tooltip.logisticsnetworks.filter.nbt.rules",
                                enabledRules, rules.size()).withStyle(ChatFormatting.DARK_GRAY));
        }
}
