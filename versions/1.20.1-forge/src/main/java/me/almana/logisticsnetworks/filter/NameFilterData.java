package me.almana.logisticsnetworks.filter;

import me.almana.logisticsnetworks.util.ItemDataUtil;

import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.item.NameFilterItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
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
        NONE, EMPTY, TOO_LONG, UNSUPPORTED, INVALID
    }

    public record ValidationResult(Pattern pattern, ValidationError error) {
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
        return getRoot(stack).getBoolean(KEY_IS_BLACKLIST);
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
        return FilterTargetType.fromOrdinal(root.getInt(KEY_TARGET_TYPE));
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
        return NameMatchScope.fromOrdinal(root.getInt(KEY_MATCH_SCOPE));
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
        return root.contains(KEY_NAME, Tag.TAG_STRING) ? root.getString(KEY_NAME) : "";
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

    public static boolean isValidRegex(String pattern) {
        return validateRegex(pattern).accepted();
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
            return new ValidationResult(Pattern.compile(expression, Pattern.CASE_INSENSITIVE), ValidationError.NONE);
        } catch (PatternSyntaxException e) {
            return new ValidationResult(null, ValidationError.INVALID);
        }
    }

    public static boolean containsName(ItemStack filter, ItemStack candidate) {
        if (candidate.isEmpty())
            return false;
        if (getTargetType(filter) != FilterTargetType.ITEMS)
            return false;

        String regex = getNameFilter(filter);
        if (regex.isEmpty())
            return false;

        ValidationResult validation = validateRegex(regex);
        if (!validation.accepted())
            return false;
        Pattern pattern = validation.pattern();

        NameMatchScope scope = getMatchScope(filter);

        if (scope == NameMatchScope.NAME || scope == NameMatchScope.BOTH) {
            String candidateName = candidate.getHoverName().getString();
            if (candidateName.length() > MAX_CANDIDATE_LENGTH)
                return false;
            if (pattern.matcher(candidateName).find())
                return true;
        }

        if ((scope == NameMatchScope.TOOLTIP || scope == NameMatchScope.BOTH)
                && FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            List<Component> tooltipLines = candidate.getTooltipLines(
                    null, TooltipFlag.NORMAL);
            for (int i = (scope == NameMatchScope.BOTH ? 1 : 0); i < tooltipLines.size(); i++) {
                String line = tooltipLines.get(i).getString();
                if (line.length() > MAX_CANDIDATE_LENGTH)
                    continue;
                if (pattern.matcher(line).find())
                    return true;
            }
        }

        return false;
    }

    public static boolean containsName(ItemStack filter, FluidStack candidate) {
        if (candidate == null || candidate.isEmpty())
            return false;
        if (getTargetType(filter) != FilterTargetType.FLUIDS)
            return false;

        String regex = getNameFilter(filter);
        if (regex.isEmpty())
            return false;

        ValidationResult validation = validateRegex(regex);
        if (!validation.accepted())
            return false;
        Pattern pattern = validation.pattern();

        String candidateName = candidate.getDisplayName().getString();
        return candidateName.length() <= MAX_CANDIDATE_LENGTH && pattern.matcher(candidateName).find();
    }

    public static boolean containsName(ItemStack filter, String chemicalId) {
        if (chemicalId == null || chemicalId.isEmpty())
            return false;
        if (getTargetType(filter) != FilterTargetType.CHEMICALS)
            return false;

        String regex = getNameFilter(filter);
        if (regex.isEmpty())
            return false;

        ValidationResult validation = validateRegex(regex);
        if (!validation.accepted())
            return false;
        Pattern pattern = validation.pattern();
        Component chemName = MekanismCompat.getChemicalTextComponent(chemicalId);
        String displayName = chemName != null ? chemName.getString() : chemicalId;
        return displayName.length() <= MAX_CANDIDATE_LENGTH && pattern.matcher(displayName).find();
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
                if (inClass) classHasToken = true; else branchHasToken = true;
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
                    if (!classHasToken) return ValidationError.INVALID;
                    inClass = false;
                    branchHasToken = true;
                    continue;
                }
                if (c != '^' || classHasToken) classHasToken = true;
                continue;
            }
            switch (c) {
                case '[' -> { inClass = true; classHasToken = false; }
                case '(', ')', '*', '+', '?', '{', '}' -> { return ValidationError.UNSUPPORTED; }
                case '|' -> {
                    if (!branchHasToken) return ValidationError.INVALID;
                    branchHasToken = false;
                }
                case '^' -> { if (branchHasToken) return ValidationError.INVALID; }
                case '$' -> {
                    if (!branchHasToken || i + 1 < expression.length() && expression.charAt(i + 1) != '|')
                        return ValidationError.INVALID;
                }
                default -> branchHasToken = true;
            }
        }
        return escaped || inClass || !branchHasToken ? ValidationError.INVALID : ValidationError.NONE;
    }

    private static String normalizeName(String name) {
        if (name == null)
            return null;
        String s = name.trim();
        return s.isEmpty() ? null : s;
    }

    private static CompoundTag getRoot(ItemStack stack) {
        CompoundTag custom = ItemDataUtil.getCustomData(stack);
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? custom.getCompound(KEY_ROOT) : new CompoundTag();
    }

    private static void updateRoot(ItemStack stack, java.util.function.Consumer<CompoundTag> modifier) {
        ItemDataUtil.updateCustomData(stack, customTag -> {
            CompoundTag root = customTag.contains(KEY_ROOT, Tag.TAG_COMPOUND)
                    ? customTag.getCompound(KEY_ROOT)
                    : new CompoundTag();

            modifier.accept(root);

            if (root.isEmpty()) {
                customTag.remove(KEY_ROOT);
            } else {
                customTag.put(KEY_ROOT, root);
            }
        });
    }
}


