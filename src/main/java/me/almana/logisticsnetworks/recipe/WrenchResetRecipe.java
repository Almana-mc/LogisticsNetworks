package me.almana.logisticsnetworks.recipe;

import com.mojang.serialization.MapCodec;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class WrenchResetRecipe extends CustomRecipe {

    public static final WrenchResetRecipe INSTANCE = new WrenchResetRecipe();
    public static final MapCodec<WrenchResetRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchResetRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<WrenchResetRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return input.ingredientCount() == 1 && hasWrench(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return new ItemStack(Registration.WRENCH.get());
    }

    @Override
    public RecipeSerializer<WrenchResetRecipe> getSerializer() {
        return SERIALIZER;
    }

    private static boolean hasWrench(CraftingInput input) {
        for (ItemStack stack : input.items()) {
            if (stack.is(Registration.WRENCH.get())) {
                return true;
            }
        }
        return false;
    }
}
