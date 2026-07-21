package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;
import appeng.crafting.pattern.EncodedSmithingTablePattern;
import appeng.crafting.pattern.EncodedStonecuttingPattern;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AE2PatternHelper {

    private AE2PatternHelper() {
    }

    static boolean isPattern(ItemStack stack) {
        if (stack.isEmpty()) return false;
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return false;
        return key.get(AEComponents.ENCODED_PROCESSING_PATTERN) != null
                || key.get(AEComponents.ENCODED_CRAFTING_PATTERN) != null
                || key.get(AEComponents.ENCODED_STONECUTTING_PATTERN) != null
                || key.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN) != null;
    }

    static List<AE2Compat.PatternEntry> readInputs(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return List.of();

        EncodedProcessingPattern processing = key.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing != null) {
            return fromGenericStacks(processing.sparseInputs());
        }

        EncodedCraftingPattern crafting = key.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) {
            return fromItemStacks(crafting.inputs());
        }

        EncodedStonecuttingPattern stonecutting = key.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null) {
            List<AE2Compat.PatternEntry> result = new ArrayList<>();
            if (!stonecutting.input().isEmpty()) {
                result.add(new AE2Compat.PatternEntry(stonecutting.input().copy(), 1));
            }
            return result;
        }

        EncodedSmithingTablePattern smithing = key.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) {
            List<AE2Compat.PatternEntry> result = new ArrayList<>();
            if (!smithing.template().isEmpty()) result.add(new AE2Compat.PatternEntry(smithing.template().copy(), 1));
            if (!smithing.base().isEmpty()) result.add(new AE2Compat.PatternEntry(smithing.base().copy(), 1));
            if (!smithing.addition().isEmpty()) result.add(new AE2Compat.PatternEntry(smithing.addition().copy(), 1));
            return result;
        }

        return List.of();
    }

    static List<AE2Compat.PatternEntry> readOutputs(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return List.of();

        EncodedProcessingPattern processing = key.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing != null) {
            return fromGenericStacks(processing.sparseOutputs());
        }

        EncodedCraftingPattern crafting = key.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) {
            List<AE2Compat.PatternEntry> result = new ArrayList<>();
            if (!crafting.result().isEmpty()) {
                result.add(new AE2Compat.PatternEntry(crafting.result().copy(), crafting.result().getCount()));
            }
            return result;
        }

        EncodedStonecuttingPattern stonecutting = key.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null) {
            List<AE2Compat.PatternEntry> result = new ArrayList<>();
            if (!stonecutting.output().isEmpty()) {
                result.add(new AE2Compat.PatternEntry(stonecutting.output().copy(), stonecutting.output().getCount()));
            }
            return result;
        }

        EncodedSmithingTablePattern smithing = key.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) {
            List<AE2Compat.PatternEntry> result = new ArrayList<>();
            if (!smithing.resultItem().isEmpty()) {
                result.add(new AE2Compat.PatternEntry(smithing.resultItem().copy(), smithing.resultItem().getCount()));
            }
            return result;
        }

        return List.of();
    }

    private static List<AE2Compat.PatternEntry> fromGenericStacks(List<GenericStack> stacks) {
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null) continue;
            AEKey what = stack.what();
            if (!(what instanceof AEItemKey itemKey)) continue;
            ItemStack item = itemKey.toStack();
            int amount = (int) Math.min(stack.amount(), Integer.MAX_VALUE);
            if (amount <= 0) amount = 1;
            mergeStack(merged, item, amount);
        }
        return toEntries(merged);
    }

    private static List<AE2Compat.PatternEntry> fromItemStacks(List<ItemStack> stacks) {
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            int amount = Math.max(1, stack.getCount());
            mergeStack(merged, stack.copyWithCount(1), amount);
        }
        return toEntries(merged);
    }

    private static void mergeStack(Map<ItemStack, Integer> merged, ItemStack stack, int amount) {
        for (Map.Entry<ItemStack, Integer> entry : merged.entrySet()) {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                entry.setValue(entry.getValue() + amount);
                return;
            }
        }
        merged.put(stack.copyWithCount(1), amount);
    }

    private static List<AE2Compat.PatternEntry> toEntries(Map<ItemStack, Integer> merged) {
        List<AE2Compat.PatternEntry> result = new ArrayList<>(merged.size());
        for (Map.Entry<ItemStack, Integer> entry : merged.entrySet()) {
            result.add(new AE2Compat.PatternEntry(entry.getKey().copy(), entry.getValue()));
        }
        return result;
    }
}
