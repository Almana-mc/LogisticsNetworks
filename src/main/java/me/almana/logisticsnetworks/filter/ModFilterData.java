package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.FilterSettings;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.ModFilterConfig;
import me.almana.logisticsnetworks.item.ModFilterItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ModFilterData {

    private ModFilterData() {
    }

    public static boolean isModFilter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ModFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isModFilter(stack))
            return false;
        LegacyComponentMigration.migrateModFilter(stack);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isModFilter(stack))
            return;

        LegacyComponentMigration.migrateModFilter(stack);
        FilterSettingsData.setBlacklist(stack, isBlacklist);
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isModFilter(stack))
            return FilterTargetType.ITEMS;
        LegacyComponentMigration.migrateModFilter(stack);
        return FilterSettingsData.get(stack).target();
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isModFilter(stack))
            return;

        LegacyComponentMigration.migrateModFilter(stack);
        FilterSettingsData.setTarget(stack, type);
    }

    public static List<String> getModFilters(ItemStack stack) {
        if (!isModFilter(stack))
            return List.of();
        LegacyComponentMigration.migrateModFilter(stack);
        return stack.getOrDefault(LogisticsDataComponents.MOD_FILTER, new ModFilterConfig(List.of())).namespaces();
    }

    public static boolean hasAnyMods(ItemStack stack) {
        return !getModFilters(stack).isEmpty();
    }

    record ModFilterView(FilterTargetType targetType, boolean blacklist, List<String> namespaces) {
    }

    record CachedModView(@Nullable FilterSettings settings, @Nullable ModFilterConfig config, ModFilterView view) {
    }

    private static ModFilterView getModFilterView(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        if (readCache == null)
            return buildModFilterView(stack);

        LegacyComponentMigration.migrateModFilter(stack);
        FilterSettings settings = stack.get(LogisticsDataComponents.FILTER_SETTINGS);
        ModFilterConfig config = stack.get(LogisticsDataComponents.MOD_FILTER);
        CachedModView cached = readCache.modViews.get(stack);
        if (cached != null && cached.settings() == settings && cached.config() == config)
            return cached.view();

        ModFilterView built = buildModFilterView(stack);
        readCache.modViews.put(stack, new CachedModView(settings, config, built));
        return built;
    }

    private static ModFilterView buildModFilterView(ItemStack stack) {
        if (!isModFilter(stack))
            return new ModFilterView(FilterTargetType.ITEMS, false, List.of());

        LegacyComponentMigration.migrateModFilter(stack);
        FilterSettings settings = FilterSettingsData.get(stack);
        return new ModFilterView(settings.target(), settings.blacklist(), getModFilters(stack));
    }

    public static boolean hasAnyMods(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        return !getModFilterView(stack, readCache).namespaces().isEmpty();
    }

    public static FilterTargetType getTargetType(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        return getModFilterView(stack, readCache).targetType();
    }

    public static boolean isBlacklist(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        return getModFilterView(stack, readCache).blacklist();
    }

    public static boolean containsMod(ItemStack stack, ItemStack candidate,
            @Nullable FilterItemData.ReadCache readCache) {
        if (candidate.isEmpty())
            return false;
        ModFilterView view = getModFilterView(stack, readCache);
        if (view.targetType() != FilterTargetType.ITEMS)
            return false;
        return checkModMatch(view.namespaces(), BuiltInRegistries.ITEM.getKey(candidate.getItem()));
    }

    public static boolean containsMod(ItemStack stack, FluidStack candidate,
            @Nullable FilterItemData.ReadCache readCache) {
        if (candidate == null || candidate.isEmpty())
            return false;
        ModFilterView view = getModFilterView(stack, readCache);
        if (view.targetType() != FilterTargetType.FLUIDS)
            return false;
        return checkModMatch(view.namespaces(), BuiltInRegistries.FLUID.getKey(candidate.getFluid()));
    }

    public static boolean containsMod(ItemStack stack, String chemicalId,
            @Nullable FilterItemData.ReadCache readCache) {
        if (chemicalId == null || chemicalId.isEmpty())
            return false;
        ModFilterView view = getModFilterView(stack, readCache);
        if (view.targetType() != FilterTargetType.CHEMICALS)
            return false;
        return checkModMatch(view.namespaces(), ResourceLocation.tryParse(chemicalId));
    }

    public static boolean addModFilter(ItemStack stack, String rawModId) {
        if (!isModFilter(stack))
            return false;
        String modId = normalizeModId(rawModId);
        if (modId == null)
            return false;

        LegacyComponentMigration.migrateModFilter(stack);
        List<String> mods = new ArrayList<>(getModFilters(stack));
        if (mods.contains(modId)) {
            return false;
        }
        mods.add(modId);
        stack.set(LogisticsDataComponents.MOD_FILTER, new ModFilterConfig(mods));
        return true;
    }

    public static boolean setSingleModFilter(ItemStack stack, String rawModId) {
        if (!isModFilter(stack))
            return false;
        String modId = normalizeModId(rawModId);
        if (modId == null)
            return false;

        LegacyComponentMigration.migrateModFilter(stack);
        if (getModFilters(stack).equals(List.of(modId))) {
            return false;
        }
        stack.set(LogisticsDataComponents.MOD_FILTER, new ModFilterConfig(List.of(modId)));
        return true;
    }

    public static boolean removeModFilter(ItemStack stack, String rawModId) {
        if (!isModFilter(stack))
            return false;
        String modId = normalizeModId(rawModId);
        if (modId == null)
            return false;

        LegacyComponentMigration.migrateModFilter(stack);
        List<String> mods = new ArrayList<>(getModFilters(stack));
        if (!mods.remove(modId)) {
            return false;
        }
        if (mods.isEmpty()) {
            stack.remove(LogisticsDataComponents.MOD_FILTER);
        } else {
            stack.set(LogisticsDataComponents.MOD_FILTER, new ModFilterConfig(mods));
        }
        return true;
    }

    public static boolean containsMod(ItemStack stack, ItemStack candidate) {
        if (candidate.isEmpty())
            return false;
        if (getTargetType(stack) != FilterTargetType.ITEMS)
            return false;

        return checkModMatch(stack, BuiltInRegistries.ITEM.getKey(candidate.getItem()));
    }

    public static boolean containsMod(ItemStack stack, FluidStack candidate) {
        if (candidate == null || candidate.isEmpty())
            return false;
        if (getTargetType(stack) != FilterTargetType.FLUIDS)
            return false;

        return checkModMatch(stack, BuiltInRegistries.FLUID.getKey(candidate.getFluid()));
    }

    private static boolean checkModMatch(ItemStack stack, ResourceLocation id) {
        return checkModMatch(getModFilters(stack), id);
    }

    private static boolean checkModMatch(List<String> namespaces, ResourceLocation id) {
        if (id == null)
            return false;
        String namespace = id.getNamespace();

        for (String mod : namespaces) {
            if (mod.equals(namespace))
                return true;
        }
        return false;
    }

    public static boolean containsMod(ItemStack stack, String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty())
            return false;
        if (getTargetType(stack) != FilterTargetType.CHEMICALS)
            return false;

        ResourceLocation id = ResourceLocation.tryParse(chemicalId);
        return checkModMatch(stack, id);
    }

    private static String normalizeModId(String modId) {
        if (modId == null)
            return null;
        String s = modId.trim().toLowerCase();
        if (s.isEmpty())
            return null;

        // "minecraft:stone" -> "minecraft"
        int colon = s.indexOf(':');
        if (colon != -1) {
            return s.substring(0, colon); // just the namespace
        }
        return s;
    }

}
