package me.almana.logisticsnetworks.integration.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.EncodedPatternItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AE2PatternHelper {

    private AE2PatternHelper() {
    }

    static boolean isPattern(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof EncodedPatternItem;
    }

    static List<AE2Compat.PatternEntry> readInputs(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        if (!(stack.getItem() instanceof EncodedPatternItem patternItem)) return List.of();

        try {
            var decoded = patternItem.decode(stack, null, false);
            if (decoded == null) return List.of();

            if (decoded instanceof AEProcessingPattern processing) {
                return fromGenericStacks(processing.getSparseInputs());
            }
            if (decoded instanceof AECraftingPattern crafting) {
                return fromGenericStacks(crafting.getSparseInputs());
            }
            if (decoded instanceof AEStonecuttingPattern stonecutting) {
                return fromPatternInputs(stonecutting);
            }
            if (decoded instanceof AESmithingTablePattern smithing) {
                return fromPatternInputs(smithing);
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    static List<AE2Compat.PatternEntry> readOutputs(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        if (!(stack.getItem() instanceof EncodedPatternItem patternItem)) return List.of();

        try {
            var decoded = patternItem.decode(stack, null, false);
            if (decoded == null) return List.of();

            if (decoded instanceof AEProcessingPattern processing) {
                return fromGenericStacks(processing.getSparseOutputs());
            }
            if (decoded instanceof AECraftingPattern crafting) {
                List<AE2Compat.PatternEntry> result = new ArrayList<>();
                GenericStack output = crafting.getPrimaryOutput();
                if (output != null && output.what() instanceof AEItemKey itemKey) {
                    result.add(new AE2Compat.PatternEntry(itemKey.toStack(), (int) Math.min(output.amount(), Integer.MAX_VALUE)));
                }
                return result;
            }
            if (decoded instanceof AEStonecuttingPattern stonecutting) {
                List<AE2Compat.PatternEntry> result = new ArrayList<>();
                GenericStack output = stonecutting.getPrimaryOutput();
                if (output != null && output.what() instanceof AEItemKey itemKey) {
                    result.add(new AE2Compat.PatternEntry(itemKey.toStack(), (int) Math.min(output.amount(), Integer.MAX_VALUE)));
                }
                return result;
            }
            if (decoded instanceof AESmithingTablePattern smithing) {
                List<AE2Compat.PatternEntry> result = new ArrayList<>();
                GenericStack output = smithing.getPrimaryOutput();
                if (output != null && output.what() instanceof AEItemKey itemKey) {
                    result.add(new AE2Compat.PatternEntry(itemKey.toStack(), (int) Math.min(output.amount(), Integer.MAX_VALUE)));
                }
                return result;
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private static List<AE2Compat.PatternEntry> fromPatternInputs(IPatternDetails pattern) {
        List<GenericStack> stacks = new ArrayList<>();
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length > 0 && possible[0] != null) {
                stacks.add(possible[0]);
            }
        }
        return fromGenericStacks(stacks.toArray(new GenericStack[0]));
    }

    private static List<AE2Compat.PatternEntry> fromGenericStacks(GenericStack[] stacks) {
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        for (GenericStack gs : stacks) {
            if (gs == null) continue;
            AEKey what = gs.what();
            if (!(what instanceof AEItemKey itemKey)) continue;
            ItemStack item = itemKey.toStack();
            int amount = (int) Math.min(gs.amount(), Integer.MAX_VALUE);
            if (amount <= 0) amount = 1;

            boolean found = false;
            for (Map.Entry<ItemStack, Integer> e : merged.entrySet()) {
                if (ItemStack.isSameItemSameTags(e.getKey(), item)) {
                    e.setValue(e.getValue() + amount);
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.put(item, amount);
            }
        }

        List<AE2Compat.PatternEntry> result = new ArrayList<>(merged.size());
        for (Map.Entry<ItemStack, Integer> e : merged.entrySet()) {
            result.add(new AE2Compat.PatternEntry(e.getKey().copy(), e.getValue()));
        }
        return result;
    }
}
