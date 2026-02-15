package me.almana.logisticsnetworks.registration;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.*;
import me.almana.logisticsnetworks.recipe.FilterCopyClearRecipe;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

public class Registration {

        public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,
                        Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM,
                        Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
                        .create(Registries.CREATIVE_MODE_TAB, Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,
                        Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
                        .create(Registries.RECIPE_SERIALIZER, Logisticsnetworks.MOD_ID);

        // Some ugly shit I have done here....
        public static final DeferredHolder<EntityType<?>, EntityType<LogisticsNodeEntity>> LOGISTICS_NODE = ENTITIES
                        .register("logistics_node",
                                        () -> EntityType.Builder
                                                        .<LogisticsNodeEntity>of(LogisticsNodeEntity::new,
                                                                        MobCategory.MISC)
                                                        .sized(1.0f, 1.0f)
                                                        .clientTrackingRange(8)
                                                        .updateInterval(20)
                                                        .build("logistics_node"));

        public static final DeferredHolder<Item, LogisticsNodeItem> LOGISTICS_NODE_ITEM = ITEMS.register(
                        "logistics_node",
                        () -> new LogisticsNodeItem(new Item.Properties()));

        public static final DeferredHolder<Item, WrenchItem> WRENCH = ITEMS.register("wrench",
                        () -> new WrenchItem(new Item.Properties().stacksTo(1)));

        public static final DeferredHolder<Item, BaseFilterItem> SMALL_FILTER = ITEMS.register("small_filter",
                        () -> new BaseFilterItem(new Item.Properties(), 9));
        public static final DeferredHolder<Item, BaseFilterItem> MEDIUM_FILTER = ITEMS.register("medium_filter",
                        () -> new BaseFilterItem(new Item.Properties(), 18));
        public static final DeferredHolder<Item, BaseFilterItem> BIG_FILTER = ITEMS.register("big_filter",
                        () -> new BaseFilterItem(new Item.Properties(), 27));

        public static final DeferredHolder<Item, TagFilterItem> TAG_FILTER = ITEMS.register("tag_filter",
                        () -> new TagFilterItem(new Item.Properties()));
        public static final DeferredHolder<Item, AmountFilterItem> AMOUNT_FILTER = ITEMS.register("amount_filter",
                        () -> new AmountFilterItem(new Item.Properties()));
        public static final DeferredHolder<Item, DurabilityFilterItem> DURABILITY_FILTER = ITEMS
                        .register("durability_filter", () -> new DurabilityFilterItem(new Item.Properties()));
        public static final DeferredHolder<Item, NbtFilterItem> NBT_FILTER = ITEMS.register("nbt_filter",
                        () -> new NbtFilterItem(new Item.Properties()));
        public static final DeferredHolder<Item, ModFilterItem> MOD_FILTER = ITEMS.register("mod_filter",
                        () -> new ModFilterItem(new Item.Properties()));

        public static final DeferredHolder<Item, NodeUpgradeItem> IRON_UPGRADE = ITEMS.register("iron_upgrade",
                        () -> new NodeUpgradeItem(new Item.Properties(), 16, 1_000, 10_000, 10));
        public static final DeferredHolder<Item, NodeUpgradeItem> GOLD_UPGRADE = ITEMS.register("gold_upgrade",
                        () -> new NodeUpgradeItem(new Item.Properties(), 32, 5_000, 50_000, 5));
        public static final DeferredHolder<Item, NodeUpgradeItem> DIAMOND_UPGRADE = ITEMS.register("diamond_upgrade",
                        () -> new NodeUpgradeItem(new Item.Properties(), 64, 20_000, 250_000, 1));
        public static final DeferredHolder<Item, NodeUpgradeItem> NETHERITE_UPGRADE = ITEMS.register(
                        "netherite_upgrade",
                        () -> new NodeUpgradeItem(new Item.Properties(), 9_999, 1_000_000, Integer.MAX_VALUE, 1));

        public static final DeferredHolder<Item, DimensionalUpgradeItem> DIMENSIONAL_UPGRADE = ITEMS.register(
                        "dimensional_upgrade",
                        () -> new DimensionalUpgradeItem(new Item.Properties()));

        public static final DeferredHolder<MenuType<?>, MenuType<NodeMenu>> NODE_MENU = MENUS.register("node_menu",
                        () -> IMenuTypeExtension.create(NodeMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<FilterMenu>> FILTER_MENU = MENUS.register(
                        "filter_menu",
                        () -> IMenuTypeExtension.create(FilterMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<ClipboardMenu>> CLIPBOARD_MENU = MENUS.register(
                        "clipboard_menu",
                        () -> IMenuTypeExtension.create(ClipboardMenu::new));

        public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<FilterCopyClearRecipe>> FILTER_COPY_CLEAR_RECIPE = RECIPE_SERIALIZERS
                        .register("filter_copy_clear",
                                        () -> new SimpleCraftingRecipeSerializer<>(FilterCopyClearRecipe::new));

        public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register(
                        "logistics_tab",
                        () -> CreativeModeTab.builder()
                                        .title(Component.translatable("itemGroup." + Logisticsnetworks.MOD_ID))
                                        .icon(() -> new ItemStack(WRENCH.get()))
                                        .displayItems((params, output) -> {
                                                ITEMS.getEntries().stream()
                                                                .map(Supplier::get)
                                                                .forEach(output::accept);
                                        })
                                        .build());

        public static void init(IEventBus modEventBus) {
                ENTITIES.register(modEventBus);
                ITEMS.register(modEventBus);
                MENUS.register(modEventBus);
                RECIPE_SERIALIZERS.register(modEventBus);
                CREATIVE_TABS.register(modEventBus);
        }
}
