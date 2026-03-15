package me.almana.logisticsnetworks.registration;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.AmountFilterItem;
import me.almana.logisticsnetworks.item.BaseFilterItem;
import me.almana.logisticsnetworks.item.DimensionalUpgradeItem;
import me.almana.logisticsnetworks.item.DurabilityFilterItem;
import me.almana.logisticsnetworks.item.LogisticsNodeItem;
import me.almana.logisticsnetworks.item.ArsSourceUpgradeItem;
import me.almana.logisticsnetworks.item.MekanismChemicalUpgradeItem;
import me.almana.logisticsnetworks.item.ModFilterItem;
import me.almana.logisticsnetworks.item.NameFilterItem;
import me.almana.logisticsnetworks.item.NbtFilterItem;
import me.almana.logisticsnetworks.item.NodeUpgradeItem;
import me.almana.logisticsnetworks.item.PatternSetterItem;
import me.almana.logisticsnetworks.item.SlotFilterItem;
import me.almana.logisticsnetworks.item.TagFilterItem;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.block.ComputerBlock;
import me.almana.logisticsnetworks.recipe.FilterCopyClearRecipe;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.MassPlacementMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.menu.PatternSetterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class Registration {

        public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,
                        Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK,
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
        public static final RegistryObject<EntityType<LogisticsNodeEntity>> LOGISTICS_NODE = ENTITIES
                        .register("logistics_node",
                                        () -> EntityType.Builder
                                                        .<LogisticsNodeEntity>of(LogisticsNodeEntity::new,
                                                                        MobCategory.MISC)
                                                        .sized(1.0f, 1.0f)
                                                        .clientTrackingRange(8)
                                                        .updateInterval(20)
                                                        .build("logistics_node"));

        public static final RegistryObject<LogisticsNodeItem> LOGISTICS_NODE_ITEM = ITEMS.register(
                        "logistics_node",
                        () -> new LogisticsNodeItem(new Item.Properties()));

        public static final RegistryObject<ComputerBlock> COMPUTER_BLOCK = BLOCKS.register("computer",
                        () -> new ComputerBlock());
        public static final RegistryObject<BlockItem> COMPUTER_ITEM = ITEMS.register("computer",
                        () -> new BlockItem(COMPUTER_BLOCK.get(), new Item.Properties()));

        public static final RegistryObject<WrenchItem> WRENCH = ITEMS.register("wrench",
                        () -> new WrenchItem(new Item.Properties().stacksTo(1)));

        public static final RegistryObject<BaseFilterItem> SMALL_FILTER = ITEMS.register("small_filter",
                        () -> new BaseFilterItem(new Item.Properties(), 9));
        public static final RegistryObject<BaseFilterItem> MEDIUM_FILTER = ITEMS.register("medium_filter",
                        () -> new BaseFilterItem(new Item.Properties(), 18));
        public static final RegistryObject<BaseFilterItem> BIG_FILTER = ITEMS.register("big_filter",
                        () -> new BaseFilterItem(new Item.Properties(), 27));

        public static final RegistryObject<TagFilterItem> TAG_FILTER = ITEMS.register("tag_filter",
                        () -> new TagFilterItem(new Item.Properties()));
        public static final RegistryObject<AmountFilterItem> AMOUNT_FILTER = ITEMS.register("amount_filter",
                        () -> new AmountFilterItem(new Item.Properties()));
        public static final RegistryObject<DurabilityFilterItem> DURABILITY_FILTER = ITEMS
                        .register("durability_filter", () -> new DurabilityFilterItem(new Item.Properties()));
        public static final RegistryObject<NbtFilterItem> NBT_FILTER = ITEMS.register("nbt_filter",
                        () -> new NbtFilterItem(new Item.Properties()));
        public static final RegistryObject<ModFilterItem> MOD_FILTER = ITEMS.register("mod_filter",
                        () -> new ModFilterItem(new Item.Properties()));
        public static final RegistryObject<SlotFilterItem> SLOT_FILTER = ITEMS.register("slot_filter",
                        () -> new SlotFilterItem(new Item.Properties()));
        public static final RegistryObject<NameFilterItem> NAME_FILTER = ITEMS.register("name_filter",
                        () -> new NameFilterItem(new Item.Properties()));

        public static final RegistryObject<NodeUpgradeItem> IRON_UPGRADE = ITEMS.register("iron_upgrade",
                        () -> new NodeUpgradeItem(new Item.Properties(), 16, 1_000, 10_000, 10));
        public static final RegistryObject<NodeUpgradeItem> GOLD_UPGRADE = ITEMS.register("gold_upgrade",
                        () -> new NodeUpgradeItem(new Item.Properties(), 32, 5_000, 50_000, 5));
        public static final RegistryObject<NodeUpgradeItem> DIAMOND_UPGRADE = ITEMS.register("diamond_upgrade",
                        () -> new NodeUpgradeItem(new Item.Properties(), 64, 20_000, 250_000, 1));
        public static final RegistryObject<NodeUpgradeItem> NETHERITE_UPGRADE = ITEMS.register(
                        "netherite_upgrade",
                        () -> new NodeUpgradeItem(new Item.Properties(), 10_000, 1_000_000, Integer.MAX_VALUE, 1));

        public static final RegistryObject<DimensionalUpgradeItem> DIMENSIONAL_UPGRADE = ITEMS.register(
                        "dimensional_upgrade",
                        () -> new DimensionalUpgradeItem(new Item.Properties()));

        public static final RegistryObject<MekanismChemicalUpgradeItem> MEKANISM_CHEMICAL_UPGRADE = ITEMS
                        .register(
                                        "mekanism_chemical_upgrade",
                                        () -> new MekanismChemicalUpgradeItem(new Item.Properties()));

        public static final RegistryObject<ArsSourceUpgradeItem> ARS_SOURCE_UPGRADE = ITEMS
                        .register(
                                        "ars_source_upgrade",
                                        () -> new ArsSourceUpgradeItem(new Item.Properties()));

        public static final RegistryObject<PatternSetterItem> PATTERN_SETTER = ITEMS
                        .register(
                                        "pattern_setter",
                                        () -> new PatternSetterItem(new Item.Properties()));

        public static final RegistryObject<MenuType<NodeMenu>> NODE_MENU = MENUS.register("node_menu",
                        () -> IForgeMenuType.create(NodeMenu::new));
        public static final RegistryObject<MenuType<FilterMenu>> FILTER_MENU = MENUS.register(
                        "filter_menu",
                        () -> IForgeMenuType.create(FilterMenu::new));
        public static final RegistryObject<MenuType<ClipboardMenu>> CLIPBOARD_MENU = MENUS.register(
                        "clipboard_menu",
                        () -> IForgeMenuType.create(ClipboardMenu::new));
        public static final RegistryObject<MenuType<MassPlacementMenu>> MASS_PLACEMENT_MENU = MENUS
                        .register(
                                        "mass_placement_menu",
                                        () -> IForgeMenuType.create(MassPlacementMenu::new));
        public static final RegistryObject<MenuType<PatternSetterMenu>> PATTERN_SETTER_MENU = MENUS
                        .register(
                                        "pattern_setter_menu",
                                        () -> IForgeMenuType.create(PatternSetterMenu::new));
        public static final RegistryObject<MenuType<ComputerMenu>> COMPUTER_MENU = MENUS.register(
                        "computer_menu",
                        () -> IForgeMenuType.create(ComputerMenu::new));

        public static final RegistryObject<SimpleCraftingRecipeSerializer<FilterCopyClearRecipe>> FILTER_COPY_CLEAR_RECIPE = RECIPE_SERIALIZERS
                        .register("filter_copy_clear",
                                        () -> new SimpleCraftingRecipeSerializer<>(FilterCopyClearRecipe::new));

        public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register(
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
                BLOCKS.register(modEventBus);
                ITEMS.register(modEventBus);
                MENUS.register(modEventBus);
                RECIPE_SERIALIZERS.register(modEventBus);
                CREATIVE_TABS.register(modEventBus);
        }
}


