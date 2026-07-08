package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.NameFilterItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class NameFilterData {

    public static final int MAX_EXPRESSION_LENGTH = 128;
    public static final int MAX_CANDIDATE_LENGTH = 512;

    private static final String KEY_ROOT = "ln_name_filter";
    private static final String KEY_IS_BLACKLIST = "blacklist";
    private static final String KEY_NAME = "name";
    private static final String KEY_TARGET_TYPE = "target";
    private static final String KEY_MATCH_SCOPE = "scope";

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
        return getRoot(stack).getBooleanOr(KEY_IS_BLACKLIST, false);
    }

    public static void setBlacklist(ItemStack stack, boolean isBlacklist) {
        if (!isNameFilter(stack))
            return;

        updateRoot(stack, root -> {
            if (isBlacklist) {
                root.putBoolean(KEY_IS_BLACKLIST, true);
            } else {
                root.remove(KEY_IS_BLACKLIST);
            }
        });
    }

    public static FilterTargetType getTargetType(ItemStack stack) {
        if (!isNameFilter(stack))
            return FilterTargetType.ITEMS;
        CompoundTag root = getRoot(stack);
        return FilterTargetType.fromOrdinal(root.getIntOr(KEY_TARGET_TYPE, FilterTargetType.ITEMS.ordinal()));
    }

    public static void setTargetType(ItemStack stack, FilterTargetType type) {
        if (!isNameFilter(stack))
            return;

        FilterTargetType target = type == null ? FilterTargetType.ITEMS : type;
        updateRoot(stack, root -> {
            if (target == FilterTargetType.ITEMS) {
                root.remove(KEY_TARGET_TYPE);
            } else {
                root.putInt(KEY_TARGET_TYPE, target.ordinal());
            }
        });
    }

    public static NameMatchScope getMatchScope(ItemStack stack) {
        if (!isNameFilter(stack))
            return NameMatchScope.NAME;
        CompoundTag root = getRoot(stack);
        return NameMatchScope.fromOrdinal(root.getIntOr(KEY_MATCH_SCOPE, NameMatchScope.NAME.ordinal()));
    }

    public static void setMatchScope(ItemStack stack, NameMatchScope scope) {
        if (!isNameFilter(stack))
            return;

        NameMatchScope s = scope == null ? NameMatchScope.NAME : scope;
        updateRoot(stack, root -> {
            if (s == NameMatchScope.NAME) {
                root.remove(KEY_MATCH_SCOPE);
            } else {
                root.putInt(KEY_MATCH_SCOPE, s.ordinal());
            }
        });
    }

    public static String getNameFilter(ItemStack stack) {
        if (!isNameFilter(stack))
            return "";
        CompoundTag root = getRoot(stack);
        return root.getStringOr(KEY_NAME, "");
    }

    public static void setNameFilter(ItemStack stack, String name) {
        if (!isNameFilter(stack))
            return;

        updateRoot(stack, root -> {
            String normalized = normalizeName(name);
            if (normalized == null || normalized.isEmpty()) {
                root.remove(KEY_NAME);
            } else {
                root.putString(KEY_NAME, normalized);
            }
        });
    }

    public static boolean hasNameFilter(ItemStack stack) {
        return !getNameFilter(stack).isEmpty();
    }

    record NameFilterView(FilterTargetType targetType, boolean blacklist, String expression,
            ValidationResult pattern) {
    }

    record CachedNameView(@Nullable CustomData key, NameFilterView view) {
    }

    private static NameFilterView getNameFilterView(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        if (readCache == null)
            return buildNameFilterView(stack, null);

        CustomData currentKey = stack.get(DataComponents.CUSTOM_DATA);
        CachedNameView cached = readCache.nameViews.get(stack);
        if (cached != null && cached.key() == currentKey)
            return cached.view();

        NameFilterView built = buildNameFilterView(stack, readCache);
        readCache.nameViews.put(stack, new CachedNameView(currentKey, built));
        return built;
    }

    private static NameFilterView buildNameFilterView(ItemStack stack, @Nullable FilterItemData.ReadCache readCache) {
        if (!isNameFilter(stack))
            return new NameFilterView(FilterTargetType.ITEMS, false, "", validateRegex(""));

        CompoundTag root = getRoot(stack);
        FilterTargetType targetType = FilterTargetType
                .fromOrdinal(root.getIntOr(KEY_TARGET_TYPE, FilterTargetType.ITEMS.ordinal()));
        boolean blacklist = root.getBooleanOr(KEY_IS_BLACKLIST, false);
        String expression = root.getStringOr(KEY_NAME, "");
        return new NameFilterView(targetType, blacklist, expression, resolveRegex(expression, readCache));
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

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return custom.getCompound(KEY_ROOT).orElseGet(CompoundTag::new);
    }

    private static void updateRoot(ItemStack stack, Consumer<CompoundTag> modifier) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, customTag -> {
            CompoundTag root = customTag.getCompound(KEY_ROOT).orElseGet(CompoundTag::new);

            modifier.accept(root);

            if (root.isEmpty()) {
                customTag.remove(KEY_ROOT);
            } else {
                customTag.put(KEY_ROOT, root);
            }
        });
    }
}
