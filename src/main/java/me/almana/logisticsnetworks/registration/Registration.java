package me.almana.logisticsnetworks.registration;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.block.ComputerBlock;
import me.almana.logisticsnetworks.block.ComputerBlockEntity;
import me.almana.logisticsnetworks.item.BaseFilterItem;
import me.almana.logisticsnetworks.item.DimensionalUpgradeItem;
import me.almana.logisticsnetworks.item.LogisticsNodeItem;
import me.almana.logisticsnetworks.item.ArsSourceUpgradeItem;
import me.almana.logisticsnetworks.item.MekanismChemicalUpgradeItem;
import me.almana.logisticsnetworks.item.PatternSetterItem;
import me.almana.logisticsnetworks.item.ModFilterItem;
import me.almana.logisticsnetworks.item.NameFilterItem;
import me.almana.logisticsnetworks.item.NodeUpgradeItem;
import me.almana.logisticsnetworks.item.SlotFilterItem;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.menu.ClipboardMenu;
import me.almana.logisticsnetworks.menu.ComputerMenu;
import me.almana.logisticsnetworks.menu.FilterMenu;
import me.almana.logisticsnetworks.menu.MassPlacementMenu;
import me.almana.logisticsnetworks.menu.NodeMenu;
import me.almana.logisticsnetworks.menu.PatternSetterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.function.Supplier;

public class Registration {

        public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,
                        Logisticsnetworks.MOD_ID);
        public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Logisticsnetworks.MOD_ID);
        public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
                        .create(Registries.CREATIVE_MODE_TAB, Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,
                        Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
                        .create(Registries.BLOCK_ENTITY_TYPE, Logisticsnetworks.MOD_ID);
        public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
                        .create(Registries.RECIPE_SERIALIZER, Logisticsnetworks.MOD_ID);

        // Some ugly shit I have done here....
        public static final DeferredHolder<EntityType<?>, EntityType<LogisticsNodeEntity>> LOGISTICS_NODE = ENTITIES
                        .register("logistics_node",
                                        Registration::createLogisticsNodeType);

        public static final DeferredItem<LogisticsNodeItem> LOGISTICS_NODE_ITEM = ITEMS.register(
                        "logistics_node",
                        id -> new LogisticsNodeItem(itemProperties(id)));

        public static final DeferredBlock<ComputerBlock> COMPUTER_BLOCK = BLOCKS.register("computer",
                        id -> new ComputerBlock(computerBlockProperties(id)));
        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES
                        .register("computer", Registration::createComputerBlockEntityType);
        public static final DeferredItem<BlockItem> COMPUTER_ITEM = ITEMS.register("computer",
                        id -> new BlockItem(COMPUTER_BLOCK.get(), blockItemProperties(id)));

        public static final DeferredItem<WrenchItem> WRENCH = ITEMS.register("wrench",
                        id -> new WrenchItem(itemProperties(id).stacksTo(1)));

        public static final DeferredItem<BaseFilterItem> SMALL_FILTER = ITEMS.register("small_filter",
                        id -> new BaseFilterItem(itemProperties(id), 9));
        public static final DeferredItem<BaseFilterItem> MEDIUM_FILTER = ITEMS.register("medium_filter",
                        id -> new BaseFilterItem(itemProperties(id), 18));
        public static final DeferredItem<BaseFilterItem> BIG_FILTER = ITEMS.register("big_filter",
                        id -> new BaseFilterItem(itemProperties(id), 27));

        public static final DeferredItem<ModFilterItem> MOD_FILTER = ITEMS.register("mod_filter",
                        id -> new ModFilterItem(itemProperties(id)));
        public static final DeferredItem<SlotFilterItem> SLOT_FILTER = ITEMS.register("slot_filter",
                        id -> new SlotFilterItem(itemProperties(id)));
        public static final DeferredItem<NameFilterItem> NAME_FILTER = ITEMS.register("name_filter",
                        id -> new NameFilterItem(itemProperties(id)));

        public static final DeferredItem<NodeUpgradeItem> IRON_UPGRADE = ITEMS.register("iron_upgrade",
                        id -> new NodeUpgradeItem(itemProperties(id), 16, 1_000, 10_000, 10));
        public static final DeferredItem<NodeUpgradeItem> GOLD_UPGRADE = ITEMS.register("gold_upgrade",
                        id -> new NodeUpgradeItem(itemProperties(id), 32, 5_000, 50_000, 5));
        public static final DeferredItem<NodeUpgradeItem> DIAMOND_UPGRADE = ITEMS.register("diamond_upgrade",
                        id -> new NodeUpgradeItem(itemProperties(id), 64, 20_000, 250_000, 1));
        public static final DeferredItem<NodeUpgradeItem> NETHERITE_UPGRADE = ITEMS.register(
                        "netherite_upgrade",
                        id -> new NodeUpgradeItem(itemProperties(id), 10_000, 1_000_000, Integer.MAX_VALUE, 1));

        public static final DeferredItem<DimensionalUpgradeItem> DIMENSIONAL_UPGRADE = ITEMS.register(
                        "dimensional_upgrade",
                        id -> new DimensionalUpgradeItem(itemProperties(id)));

        public static final DeferredItem<MekanismChemicalUpgradeItem> MEKANISM_CHEMICAL_UPGRADE = ITEMS
                        .register(
                                        "mekanism_chemical_upgrade",
                                        id -> new MekanismChemicalUpgradeItem(itemProperties(id)));

        public static final DeferredItem<ArsSourceUpgradeItem> ARS_SOURCE_UPGRADE = ITEMS
                        .register(
                                        "ars_source_upgrade",
                                        id -> new ArsSourceUpgradeItem(itemProperties(id)));

        public static final DeferredItem<PatternSetterItem> PATTERN_SETTER = ITEMS
                        .register(
                                        "pattern_setter",
                                        id -> new PatternSetterItem(itemProperties(id)));

        public static final DeferredHolder<MenuType<?>, MenuType<NodeMenu>> NODE_MENU = MENUS.register("node_menu",
                        () -> IMenuTypeExtension.create(NodeMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<FilterMenu>> FILTER_MENU = MENUS.register(
                        "filter_menu",
                        () -> IMenuTypeExtension.create(FilterMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<ClipboardMenu>> CLIPBOARD_MENU = MENUS.register(
                        "clipboard_menu",
                        () -> IMenuTypeExtension.create(ClipboardMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<MassPlacementMenu>> MASS_PLACEMENT_MENU = MENUS
                        .register(
                                        "mass_placement_menu",
                                        () -> IMenuTypeExtension.create(MassPlacementMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<PatternSetterMenu>> PATTERN_SETTER_MENU = MENUS
                        .register(
                                        "pattern_setter_menu",
                                        () -> IMenuTypeExtension.create(PatternSetterMenu::new));
        public static final DeferredHolder<MenuType<?>, MenuType<ComputerMenu>> COMPUTER_MENU = MENUS.register(
                        "computer_menu",
                        () -> IMenuTypeExtension.create(ComputerMenu::new));

        // 26.1 recipe API pending
        /*
        public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<FilterCopyClearRecipe>> FILTER_COPY_CLEAR_RECIPE = RECIPE_SERIALIZERS
                        .register("filter_copy_clear",
                                        () -> new SimpleCraftingRecipeSerializer<>(FilterCopyClearRecipe::new));
        */

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
                BLOCKS.register(modEventBus);
                ITEMS.register(modEventBus);
                MENUS.register(modEventBus);
                BLOCK_ENTITY_TYPES.register(modEventBus);
                RECIPE_SERIALIZERS.register(modEventBus);
                CREATIVE_TABS.register(modEventBus);
        }

        private static EntityType<LogisticsNodeEntity> createLogisticsNodeType(Identifier id) {
                return net.minecraft.world.entity.EntityType.Builder
                                .<LogisticsNodeEntity>of(LogisticsNodeEntity::new, MobCategory.MISC)
                                .sized(1.0f, 1.0f)
                                .clientTrackingRange(8)
                                .updateInterval(20)
                                .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id));
        }

        private static BlockEntityType<ComputerBlockEntity> createComputerBlockEntityType() {
                return new BlockEntityType<ComputerBlockEntity>(
                                ComputerBlockEntity::new,
                                java.util.Set.of(COMPUTER_BLOCK.get()));
        }

        private static Item.Properties itemProperties(Identifier id) {
                return new Item.Properties()
                                .setId(ResourceKey.create(Registries.ITEM, id));
        }

        private static Item.Properties blockItemProperties(Identifier id) {
                return itemProperties(id)
                                .useBlockDescriptionPrefix();
        }

        private static BlockBehaviour.Properties computerBlockProperties(Identifier id) {
                return BlockBehaviour.Properties.of()
                                .setId(ResourceKey.create(Registries.BLOCK, id))
                                .strength(0.5f)
                                .sound(SoundType.METAL)
                                .noOcclusion();
        }

        public static BlockEntityType<ComputerBlockEntity> computerBlockEntityType() {
                return COMPUTER_BLOCK_ENTITY.get();
        }

        public static Item logisticsNodeItem() {
                return LOGISTICS_NODE_ITEM.get();
        }
}
