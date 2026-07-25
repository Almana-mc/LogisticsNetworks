package me.almana.logisticsnetworks.datagen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.recipe.FilterCopyClearRecipe;
import me.almana.logisticsnetworks.recipe.GuideRecipe;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {

    private static final TagKey<Item> C_GLASS_PANES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "glass_panes"));

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Registration.LOGISTICS_NODE_ITEM.get())
                .pattern("DRD").pattern("BHB").pattern("DED")
                .define('D', Items.DEEPSLATE).define('R', Items.REDSTONE).define('B', Items.IRON_BARS)
                .define('H', Items.HOPPER).define('E', Items.ENDER_PEARL)
                .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL)).save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Registration.COMPUTER_ITEM.get())
                .pattern("III").pattern("IGI").pattern("ICI")
                .define('I', Items.IRON_INGOT).define('G', Items.GLASS).define('C', Items.COMPARATOR)
                .unlockedBy("has_comparator", has(Items.COMPARATOR)).save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Registration.WRENCH.get())
                .pattern("T T").pattern("BGB").pattern("PPP")
                .define('T', Items.REDSTONE_TORCH).define('B', Items.STONE_BUTTON)
                .define('G', C_GLASS_PANES).define('P', Items.POLISHED_BLACKSTONE)
                .unlockedBy("has_blackstone", has(Items.POLISHED_BLACKSTONE)).save(output);

        upgrade(output, Registration.IRON_UPGRADE.get(), Items.IRON_INGOT);
        upgrade(output, Registration.GOLD_UPGRADE.get(), Items.GOLD_INGOT);
        upgrade(output, Registration.DIAMOND_UPGRADE.get(), Items.DIAMOND);
        upgrade(output, Registration.NETHERITE_UPGRADE.get(), Items.NETHERITE_INGOT);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Registration.DIMENSIONAL_UPGRADE.get())
                .pattern("CEC").pattern("BSB").pattern("CEC")
                .define('C', Items.CRYING_OBSIDIAN).define('E', Items.END_CRYSTAL)
                .define('B', Items.DRAGON_BREATH).define('S', Items.NETHER_STAR)
                .unlockedBy("has_nether_star", has(Items.NETHER_STAR)).save(output);

        SpecialRecipeBuilder.special(FilterCopyClearRecipe::new)
                .save(output, ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "filter_copy_clear"));
        SpecialRecipeBuilder.special(GuideRecipe::new)
                .save(output.withConditions(new ModLoadedCondition("guideme")),
                        ResourceLocation.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "guide"));
    }

    private static void upgrade(RecipeOutput output, Item result, Item corner) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("XHX").pattern("CSC").pattern("XHX")
                .define('X', corner).define('H', Items.HOPPER).define('C', Items.CHEST)
                .define('S', Items.SMOOTH_STONE)
                .unlockedBy("has_hopper", has(Items.HOPPER)).save(output);
    }
}
