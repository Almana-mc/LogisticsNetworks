package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;
import appeng.crafting.pattern.EncodedSmithingTablePattern;
import appeng.crafting.pattern.EncodedStonecuttingPattern;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AE2PatternReader {

    private AE2PatternReader() {
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

    static List<LinkedStorage.PatternEntry> readInputs(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return List.of();
        EncodedProcessingPattern processing = key.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing != null) return fromGenericStacks(processing.sparseInputs());
        EncodedCraftingPattern crafting = key.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) return fromItemStacks(crafting.inputs());
        EncodedStonecuttingPattern stonecutting = key.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null && !stonecutting.input().isEmpty()) {
            return List.of(new LinkedStorage.PatternEntry(stonecutting.input().copy(), 1));
        }
        EncodedSmithingTablePattern smithing = key.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing == null) return List.of();
        List<LinkedStorage.PatternEntry> result = new ArrayList<>();
        if (!smithing.template().isEmpty()) result.add(new LinkedStorage.PatternEntry(smithing.template().copy(), 1));
        if (!smithing.base().isEmpty()) result.add(new LinkedStorage.PatternEntry(smithing.base().copy(), 1));
        if (!smithing.addition().isEmpty()) result.add(new LinkedStorage.PatternEntry(smithing.addition().copy(), 1));
        return result;
    }

    static List<LinkedStorage.PatternEntry> readOutputs(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return List.of();
        EncodedProcessingPattern processing = key.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing != null) return fromGenericStacks(processing.sparseOutputs());
        EncodedCraftingPattern crafting = key.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null && !crafting.result().isEmpty()) {
            return List.of(new LinkedStorage.PatternEntry(crafting.result().copy(), crafting.result().getCount()));
        }
        EncodedStonecuttingPattern stonecutting = key.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null && !stonecutting.output().isEmpty()) {
            return List.of(new LinkedStorage.PatternEntry(stonecutting.output().copy(), stonecutting.output().getCount()));
        }
        EncodedSmithingTablePattern smithing = key.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null && !smithing.resultItem().isEmpty()) {
            return List.of(new LinkedStorage.PatternEntry(smithing.resultItem().copy(), smithing.resultItem().getCount()));
        }
        return List.of();
    }

    private static List<LinkedStorage.PatternEntry> fromGenericStacks(List<GenericStack> stacks) {
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) continue;
            int amount = (int) Math.min(stack.amount(), Integer.MAX_VALUE);
            mergeStack(merged, itemKey.toStack(), Math.max(amount, 1));
        }
        return toEntries(merged);
    }

    private static List<LinkedStorage.PatternEntry> fromItemStacks(List<ItemStack> stacks) {
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            mergeStack(merged, stack.copyWithCount(1), Math.max(1, stack.getCount()));
        }
        return toEntries(merged);
    }

    private static void mergeStack(Map<ItemStack, Integer> merged, ItemStack stack, int amount) {
        for (Map.Entry<ItemStack, Integer> entry : merged.entrySet()) {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                entry.setValue(saturatingAdd(entry.getValue(), amount));
                return;
            }
        }
        merged.put(stack.copyWithCount(1), amount);
    }

    private static List<LinkedStorage.PatternEntry> toEntries(Map<ItemStack, Integer> merged) {
        List<LinkedStorage.PatternEntry> result = new ArrayList<>(merged.size());
        for (Map.Entry<ItemStack, Integer> entry : merged.entrySet()) {
            result.add(new LinkedStorage.PatternEntry(entry.getKey().copy(), entry.getValue()));
        }
        return result;
    }

    private static int saturatingAdd(int left, int right) {
        long result = (long) left + right;
        return (int) Math.min(result, Integer.MAX_VALUE);
    }
}
