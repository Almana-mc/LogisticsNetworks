package me.almana.logisticsnetworks.recipe;

import com.mojang.serialization.MapCodec;
import me.almana.logisticsnetworks.registration.ModTags;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;


public class FilterCopyClearRecipe extends CustomRecipe {

    public static final FilterCopyClearRecipe INSTANCE = new FilterCopyClearRecipe();
    public static final MapCodec<FilterCopyClearRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterCopyClearRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<FilterCopyClearRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !buildResult(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return buildResult(input);
    }

    @Override
    public RecipeSerializer<FilterCopyClearRecipe> getSerializer() {
        return SERIALIZER;
    }

    private static ItemStack buildResult(CraftingInput input) {
        Item targetItem = null;
        ItemStack configuredSource = ItemStack.EMPTY;
        int configuredCount = 0;
        int filterCount = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (!stack.is(ModTags.FILTERS)) {
                return ItemStack.EMPTY;
            }

            Item item = stack.getItem();
            if (targetItem == null) {
                targetItem = item;
            } else if (targetItem != item) {
                return ItemStack.EMPTY;
            }

            filterCount++;
            if (isConfiguredFilter(stack)) {
                configuredCount++;
                if (configuredSource.isEmpty()) {
                    configuredSource = stack;
                }
            }
        }

        if (targetItem == null) {
            return ItemStack.EMPTY;
        }

        if (filterCount == 1) {
            return new ItemStack(targetItem);
        }

        if (configuredCount == 1) {
            return configuredSource.copyWithCount(filterCount);
        }

        return ItemStack.EMPTY;
    }

    private static boolean isConfiguredFilter(ItemStack stack) {
        return me.almana.logisticsnetworks.component.FilterComponentData.isConfigured(stack, null);
    }
}
