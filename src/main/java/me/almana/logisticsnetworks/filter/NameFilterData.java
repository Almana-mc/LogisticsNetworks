package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.component.FilterSettings;
import me.almana.logisticsnetworks.component.FilterSettingsData;
import me.almana.logisticsnetworks.component.LegacyComponentMigration;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.NameFilterConfig;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.NameFilterItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class NameFilterData {

    public static final int MAX_EXPRESSION_LENGTH = 128;
    public static final int MAX_CANDIDATE_LENGTH = 512;

    public enum ValidationError {
        NONE,
        EMPTY,
        TOO_LONG,
        UNSUPPORTED,
        INVALID
    }

    public record ValidationResult(@Nullable Pattern pattern, ValidationError error) {
        public boolean accepted() {
            return error == ValidationError.NONE;
        }
    }

    private NameFilterData() {
    }

    public static boolean isNameFilter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof NameFilterItem;
    }

    public static boolean isBlacklist(ItemStack stack) {
        if (!isNameFilter(stack))
            return false;
        LegacyComponentMigration.migrateNameFilter(stack);
        return FilterSettingsData.get(stack).blacklist();
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isNameFilter(stack))
            return;

        LegacyComponentMigration.migrateNameFilter(stack);
        FilterSettingsData.setBlacklist(stack, isBlacklist);
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isNameFilter(stack))
            return FilterTargetType.ITEMS;
        LegacyComponentMigration.migrateNameFilter(stack);
        return FilterSettingsData.get(stack).target();
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isNameFilter(stack))
            return;

        LegacyComponentMigration.migrateNameFilter(stack);
        FilterSettingsData.setTarget(stack, type);
    }

    public static NameMatchScope getMatchScope(ItemStack stack) {
        if (!isNameFilter(stack))
            return NameMatchScope.NAME;
        LegacyComponentMigration.migrateNameFilter(stack);
        return getConfig(stack).scope();
    }

    public static void setMatchScope(ItemStack stack, NameMatchScope scope) {
        if (!isNameFilter(stack))
            return;

        LegacyComponentMigration.migrateNameFilter(stack);
        NameFilterConfig current = getConfig(stack);
        setConfig(stack, new NameFilterConfig(current.expression(), scope));
    }

    public static String getNameFilter(ItemStack stack) {
        if (!isNameFilter(stack))
            return "";
        LegacyComponentMigration.migrateNameFilter(stack);
        return getConfig(stack).expression();
    }

    public static void setNameFilter(ItemStack stack, String name) {
        if (!isNameFilter(stack))
            return;

        LegacyComponentMigration.migrateNameFilter(stack);
        setConfig(stack, new NameFilterConfig(normalizeName(name), getConfig(stack).scope()));
    }

    public static boolean hasNameFilter(ItemStack stack) {
        return !getNameFilter(stack).isEmpty();
    }

    record NameFilterView(FilterTargetType targetType, boolean blacklist, String expression,
            ValidationResult pattern) {
    }

    record CachedNameView(@Nullable FilterSettings settings, @Nullable NameFilterConfig config, NameFilterView view) {
    }

    private static NameFilterView getNameFilterView(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        if (readCache == null)
            return buildNameFilterView(stack, null);

        LegacyComponentMigration.migrateNameFilter(stack);
        FilterSettings settings = stack.get(LogisticsDataComponents.FILTER_SETTINGS);
        NameFilterConfig config = stack.get(LogisticsDataComponents.NAME_FILTER);
        CachedNameView cached = readCache.nameViews.get(stack);
        if (cached != null && cached.settings() == settings && cached.config() == config)
            return cached.view();

        NameFilterView built = buildNameFilterView(stack, readCache);
        readCache.nameViews.put(stack, new CachedNameView(settings, config, built));
        return built;
    }

    private static NameFilterView buildNameFilterView(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        if (!isNameFilter(stack))
            return new NameFilterView(FilterTargetType.ITEMS, false, "", validateRegex(""));

        LegacyComponentMigration.migrateNameFilter(stack);
        FilterSettings settings = FilterSettingsData.get(stack);
        String expression = getConfig(stack).expression();
        return new NameFilterView(settings.target(), settings.blacklist(), expression, resolveRegex(expression, readCache));
    }

    public static boolean hasNameFilter(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        return !getNameFilterView(stack, readCache).expression().isEmpty();
    }

    public static FilterTargetType getTargetType(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        return getNameFilterView(stack, readCache).targetType();
    }

    public static boolean isBlacklist(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        return getNameFilterView(stack, readCache).blacklist();
    }

    public static boolean isValidRegex(String expression) {
        return validateRegex(expression).accepted();
    }

    public static ValidationResult validateRegex(String expression) {
        if (expression == null || expression.isEmpty())
            return new ValidationResult(null, ValidationError.EMPTY);
        if (expression.length() > MAX_EXPRESSION_LENGTH)
            return new ValidationResult(null, ValidationError.TOO_LONG);

        ValidationError syntaxError = inspectSyntax(expression);
        if (syntaxError != ValidationError.NONE)
            return new ValidationResult(null, syntaxError);

        try {
            Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
            return new ValidationResult(pattern, ValidationError.NONE);
        } catch (PatternSyntaxException e) {
            return new ValidationResult(null, ValidationError.INVALID);
        }
    }

    public static boolean containsName(ItemStack filter, ItemStack candidate) {
        return containsName(filter, candidate, null);
    }

    public static boolean containsName(ItemStack filter, ItemStack candidate,
            @Nullable FilterItemData.ReadCache readCache) {
        if (candidate.isEmpty())
            return false;
        NameFilterView view = getNameFilterView(filter, readCache);
        if (view.targetType() != FilterTargetType.ITEMS)
            return false;
        if (view.expression().isEmpty())
            return false;

        String candidateName = candidate.getHoverName().getString();
        return matchesView(view, candidateName);
    }

    public static boolean containsName(ItemStack filter, FluidStack candidate) {
        return containsName(filter, candidate, null);
    }

    public static boolean containsName(ItemStack filter, FluidStack candidate,
            @Nullable FilterItemData.ReadCache readCache) {
        if (candidate.isEmpty())
            return false;
        NameFilterView view = getNameFilterView(filter, readCache);
        if (view.targetType() != FilterTargetType.FLUIDS)
            return false;
        if (view.expression().isEmpty())
            return false;

        String candidateName = candidate.getHoverName().getString();
        return matchesView(view, candidateName);
    }

    public static boolean containsName(ItemStack filter, String chemicalId) {
        return containsName(filter, chemicalId, null);
    }

    public static boolean containsName(ItemStack filter, String chemicalId,
            @Nullable FilterItemData.ReadCache readCache) {
        if (chemicalId == null || chemicalId.isEmpty())
            return false;
        NameFilterView view = getNameFilterView(filter, readCache);
        if (view.targetType() != FilterTargetType.CHEMICALS)
            return false;
        if (view.expression().isEmpty())
            return false;

        Component chemName = MekanismCompat.getChemicalTextComponent(chemicalId);
        String displayName = chemName != null ? chemName.getString() : chemicalId;
        return matchesView(view, displayName);
    }

    static ValidationResult resolveRegex(String expression, @Nullable FilterItemData.ReadCache readCache) {
        if (readCache == null)
            return validateRegex(expression);
        return readCache.namePatterns.computeIfAbsent(expression, NameFilterData::validateRegex);
    }

    private static boolean matchesView(NameFilterView view, String candidate) {
        if (candidate.length() > MAX_CANDIDATE_LENGTH)
            return false;

        ValidationResult result = view.pattern();
        return result.accepted() && result.pattern().matcher(candidate).find();
    }

    private static ValidationError inspectSyntax(String expression) {
        boolean escaped = false;
        boolean inClass = false;
        boolean classHasToken = false;
        boolean branchHasToken = false;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (escaped) {
                if (Character.isLetterOrDigit(c))
                    return ValidationError.UNSUPPORTED;
                escaped = false;
                if (inClass) {
                    classHasToken = true;
                } else {
                    branchHasToken = true;
                }
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (inClass) {
                if (c == '[' || c == '&' && i + 1 < expression.length() && expression.charAt(i + 1) == '&')
                    return ValidationError.UNSUPPORTED;
                if (c == ']') {
                    if (!classHasToken)
                        return ValidationError.INVALID;
                    inClass = false;
                    branchHasToken = true;
                    continue;
                }
                if (c != '^' || classHasToken)
                    classHasToken = true;
                continue;
            }

            switch (c) {
                case '[' -> {
                    inClass = true;
                    classHasToken = false;
                }
                case '(', ')', '*', '+', '?', '{', '}' -> {
                    return ValidationError.UNSUPPORTED;
                }
                case '|' -> {
                    if (!branchHasToken)
                        return ValidationError.INVALID;
                    branchHasToken = false;
                }
                case '^' -> {
                    if (branchHasToken)
                        return ValidationError.INVALID;
                }
                case '$' -> {
                    if (!branchHasToken
                            || i + 1 < expression.length() && expression.charAt(i + 1) != '|')
                        return ValidationError.INVALID;
                }
                default -> branchHasToken = true;
            }
        }

        if (escaped || inClass || !branchHasToken)
            return ValidationError.INVALID;
        return ValidationError.NONE;
    }

    private static String normalizeName(String name) {
        if (name == null)
            return null;
        String s = name.trim();
        return s.isEmpty() ? null : s;
    }

    private static NameFilterConfig getConfig(ItemStack stack) {
        return stack.getOrDefault(LogisticsDataComponents.NAME_FILTER, new NameFilterConfig("", NameMatchScope.NAME));
    }

    private static void setConfig(ItemStack stack, NameFilterConfig config) {
        if (config.expression().isEmpty() && config.scope() == NameMatchScope.NAME) {
            stack.remove(LogisticsDataComponents.NAME_FILTER);
        } else {
            stack.set(LogisticsDataComponents.NAME_FILTER, config);
        }
    }
}
