package me.almana.logisticsnetworks.integration.refinedstorage;

import com.refinedmods.refinedstorage.api.autocrafting.Ingredient;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import me.almana.logisticsnetworks.integration.storage.LinkedStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RefinedStoragePatternReader {

    private RefinedStoragePatternReader() {
    }

    static boolean isPattern(ItemStack stack, Level level) {
        return !stack.isEmpty() && RefinedStorageApi.INSTANCE.getPattern(stack, level).isPresent();
    }

    static List<LinkedStorage.PatternEntry> readInputs(ItemStack stack, Level level) {
        Pattern pattern = RefinedStorageApi.INSTANCE.getPattern(stack, level).orElse(null);
        if (pattern == null) return List.of();
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        for (Ingredient ingredient : pattern.layout().ingredients()) {
            ItemResource resource = ingredient.inputs().stream()
                    .filter(ItemResource.class::isInstance)
                    .map(ItemResource.class::cast)
                    .findFirst().orElse(null);
            if (resource != null) merge(merged, resource.toItemStack(), ingredient.amount());
        }
        return entries(merged);
    }

    static List<LinkedStorage.PatternEntry> readOutputs(ItemStack stack, Level level) {
        Pattern pattern = RefinedStorageApi.INSTANCE.getPattern(stack, level).orElse(null);
        if (pattern == null) return List.of();
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        addAmounts(merged, pattern.layout().outputs());
        addAmounts(merged, pattern.layout().byproducts());
        return entries(merged);
    }

    private static void addAmounts(Map<ItemStack, Integer> merged, List<ResourceAmount> amounts) {
        for (ResourceAmount amount : amounts) {
            if (amount.resource() instanceof ItemResource resource) {
                merge(merged, resource.toItemStack(), amount.amount());
            }
        }
    }

    private static void merge(Map<ItemStack, Integer> merged, ItemStack stack, long amount) {
        int count = (int) Math.min(amount, Integer.MAX_VALUE);
        for (Map.Entry<ItemStack, Integer> entry : merged.entrySet()) {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                entry.setValue(saturatingAdd(entry.getValue(), count));
                return;
            }
        }
        merged.put(stack.copyWithCount(1), Math.max(1, count));
    }

    private static List<LinkedStorage.PatternEntry> entries(Map<ItemStack, Integer> merged) {
        List<LinkedStorage.PatternEntry> result = new ArrayList<>(merged.size());
        merged.forEach((stack, count) -> result.add(new LinkedStorage.PatternEntry(stack.copy(), count)));
        return result;
    }

    private static int saturatingAdd(int left, int right) {
        return (int) Math.min((long) left + right, Integer.MAX_VALUE);
    }
}
