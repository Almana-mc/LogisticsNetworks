package me.almana.logisticsnetworks.datagen;

import java.util.concurrent.CompletableFuture;
import me.almana.logisticsnetworks.recipe.FilterCopyClearRecipe;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ModRecipeProvider extends RecipeProvider {

    private static final TagKey<Item> C_GLASS_PANES = commonTag("glass_panes");

    protected ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        shaped(RecipeCategory.MISC, Registration.LOGISTICS_NODE_ITEM.get())
                .pattern("ORO").pattern("HEH").pattern("OTO")
                .define('O', Items.OBSIDIAN)
                .define('R', Items.REDSTONE_BLOCK)
                .define('H', Items.HOPPER)
                .define('E', Items.ENDER_EYE)
                .define('T', Items.REDSTONE_TORCH)
                .unlockedBy("has_ender_eye", has(Items.ENDER_EYE))
                .save(output);

        shaped(RecipeCategory.MISC, Registration.COMPUTER_ITEM.get())
                .pattern("III").pattern("IGI").pattern("ICI")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS)
                .define('C', Items.COMPARATOR)
                .unlockedBy("has_comparator", has(Items.COMPARATOR))
                .save(output);

        shaped(RecipeCategory.MISC, Registration.WRENCH.get())
                .pattern("T T").pattern("BGB").pattern("PPP")
                .define('T', Items.REDSTONE_TORCH)
                .define('B', Items.STONE_BUTTON)
                .define('G', C_GLASS_PANES)
                .define('P', Items.POLISHED_BLACKSTONE)
                .unlockedBy("has_blackstone", has(Items.POLISHED_BLACKSTONE))
                .save(output);

        upgrade(Registration.IRON_UPGRADE.get(), Items.IRON_INGOT);
        upgrade(Registration.GOLD_UPGRADE.get(), Items.GOLD_INGOT);
        upgrade(Registration.DIAMOND_UPGRADE.get(), Items.DIAMOND);
        upgrade(Registration.NETHERITE_UPGRADE.get(), Items.NETHERITE_INGOT);

        shaped(RecipeCategory.MISC, Registration.DIMENSIONAL_UPGRADE.get())
                .pattern("CEC").pattern("BSB").pattern("CEC")
                .define('C', Items.CRYING_OBSIDIAN)
                .define('E', Items.END_CRYSTAL)
                .define('B', Items.DRAGON_BREATH)
                .define('S', Items.NETHER_STAR)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                .save(output);

        SpecialRecipeBuilder.special(() -> FilterCopyClearRecipe.INSTANCE)
                .save(output, "logisticsnetworks:filter_copy_clear");
    }

    private void upgrade(Item result, Item corner) {
        shaped(RecipeCategory.MISC, result)
                .pattern("XHX").pattern("CSC").pattern("XHX")
                .define('X', corner)
                .define('H', Items.HOPPER)
                .define('C', Items.CHEST)
                .define('S', Items.SMOOTH_STONE)
                .unlockedBy("has_hopper", has(Items.HOPPER))
                .save(output);
    }

    private static TagKey<Item> commonTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    public static final class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "LogisticsNetworks Recipes";
        }
    }
}
