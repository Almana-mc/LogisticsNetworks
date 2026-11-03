package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.NbtCriterion;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.data.IoSide;
import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.filter.NbtFilterData;
import me.almana.logisticsnetworks.filter.NbtPath;
import me.almana.logisticsnetworks.filter.SlotExpressionUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

final class IngredientDetailHost implements EntryDetailPage.Host {

    private static final int TITLE_W = 110;

    private final RecipeCardScreen screen;
    private RecipeCardData.Ingredient shown;
    private boolean frozen;

    IngredientDetailHost(RecipeCardScreen screen) {
        this.screen = screen;
    }

    void track() {
        frozen = false;
    }

    // Row may have moved
    void freeze() {
        frozen = true;
    }

    boolean frozen() {
        return frozen;
    }

    private RecipeCardData.Ingredient ingredient(int entry) {
        List<RecipeCardData.Ingredient> list = screen.getMenu().data().ingredients();
        if (!frozen && entry >= 0 && entry < list.size()) shown = list.get(entry);
        return shown;
    }

    // Merges shift rows, so freeze
    private void change(int entry, UnaryOperator<RecipeCardData.Ingredient> edit) {
        if (frozen || entry < 0) return;
        int size = screen.getMenu().data().ingredients().size();
        RecipeCardData.Ingredient next = edit.apply(ingredient(entry));
        screen.getMenu().edit(data -> data.withIngredient(entry, next));
        frozen = screen.getMenu().data().ingredients().size() < size;
        screen.edited();
    }

    private void changeNbt(int entry, UnaryOperator<GeneralFilterEntry.NbtConstraints> edit) {
        change(entry, ingredient -> ingredient.withNbt(edit.apply(ingredient.nbt())));
    }

    @Override
    public Rect2i panel() {
        return screen.pagePanel();
    }

    @Override
    public void resize(int minWidth, int minHeight) {
        screen.resizePage(minWidth, minHeight);
    }

    @Override
    public void restoreSize() {
        screen.resizePage(0, 0);
    }

    @Override
    public Component title(int entry) {
        String label = ingredient(entry).label().getString();
        return Component.literal(Minecraft.getInstance().font.plainSubstrByWidth(label, TITLE_W));
    }

    @Override
    public boolean shows(Part part) {
        return false;
    }

    @Override
    public String sideTooltip() {
        return "gui.logisticsnetworks.recipe_card.side.tooltip";
    }

    @Override
    public FilterTargetType kind(int entry) {
        return switch (ingredient(entry).kind()) {
            case FLUID -> FilterTargetType.FLUIDS;
            case CHEMICAL -> FilterTargetType.CHEMICALS;
            case ITEM -> FilterTargetType.ITEMS;
        };
    }

    @Override
    public String tag(int entry) {
        return ingredient(entry).tag().map(tag -> tag.location().toString()).orElse(null);
    }

    @Override
    public FluidStack fluid(int entry) {
        return ingredient(entry).fluidStack(FluidType.BUCKET_VOLUME);
    }

    @Override
    public String chemical(int entry) {
        return ingredient(entry).chemical().orElse(null);
    }

    @Override
    public ItemStack sample(int entry) {
        return ingredient(entry).item().map(StackSnapshot::toStack).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean hasEntry(int entry) {
        return true;
    }

    @Override
    public boolean isNbtOnly(int entry) {
        return false;
    }

    @Override
    public boolean hasNbt(int entry) {
        GeneralFilterEntry.NbtConstraints nbt = ingredient(entry).nbt();
        return !nbt.rules().isEmpty() || !nbt.raw().isEmpty();
    }

    @Override
    public boolean hasConfig(int entry) {
        return hasNbt(entry);
    }

    @Override
    public void renderAny(GuiGraphics g, int x, int y) {
    }

    @Override
    public void setTag(int entry, String tag, FilterTargetType kind) {
    }

    @Override
    public void clearTag(int entry) {
    }

    @Override
    public void setItem(int entry, ItemStack stack) {
    }

    @Override
    public void setFluid(int entry, FluidStack stack) {
    }

    @Override
    public void setChemical(int entry, String chemicalId) {
    }

    @Override
    public void clearEntry(int entry) {
    }

    @Override
    public void clearItem(int entry) {
    }

    @Override
    public void openTagPicker(int entry, FilterTargetType kind, ResourceLocation current, Consumer<String> picked) {
    }

    @Override
    public int batch(int entry) {
        return 0;
    }

    @Override
    public int stock(int entry) {
        return 0;
    }

    @Override
    public void setAmounts(int entry, int batch, int stock) {
    }

    @Override
    public IoSide side(int entry) {
        return ingredient(entry).side().orElse(null);
    }

    @Override
    public void setSide(int entry, IoSide side) {
        change(entry, ingredient -> ingredient.withSide(Optional.ofNullable(side)));
    }

    @Override
    public String slotExpression(int entry) {
        return SlotExpressionUtil.formatSlots(ingredient(entry).slots());
    }

    @Override
    public void setSlotExpression(int entry, String expression) {
        BitSet parsed = SlotExpressionUtil.parseMask(expression);
        if (parsed != null) change(entry, ingredient -> ingredient.withSlots(SlotExpressionUtil.bitSetToList(parsed)));
    }

    @Override
    public Component slotsToggle(int entry) {
        return Component.translatable(ingredient(entry).split() ? "gui.logisticsnetworks.recipe_card.split"
                : "gui.logisticsnetworks.recipe_card.allowed");
    }

    @Override
    public void toggleSlots(int entry) {
        change(entry, ingredient -> ingredient.withSplit(!ingredient.split()));
    }

    @Override
    public boolean nbtStrict(int entry) {
        return ingredient(entry).strict();
    }

    @Override
    public void setNbtStrict(int entry, boolean strict) {
        changeNbt(entry, nbt -> nbt.withStrict(strict));
    }

    @Override
    public List<NbtCriterion> nbtRules(int entry) {
        return ingredient(entry).nbt().rules();
    }

    @Override
    public String nbtRaw(int entry) {
        String raw = ingredient(entry).nbt().raw();
        return raw.isEmpty() ? null : raw;
    }

    // Mirrors FilterMenu.addSlotNbtRule
    @Override
    public void addNbtRule(int entry, NbtPath path, String operator, String fallbackValue) {
        ItemStack sample = sample(entry);
        Tag value = sample.isEmpty() ? null
                : NbtFilterData.resolvePathValue(sample, path, Minecraft.getInstance().level.registryAccess());
        if (value == null) value = NbtFilterData.getDefaultValue(path.toString());
        if (value == null && !fallbackValue.isEmpty()) value = NbtFilterData.parseValueString(fallbackValue);
        if (value == null) return;
        NbtCriterion added = new NbtCriterion(path, operator, value);
        changeNbt(entry, nbt -> {
            List<NbtCriterion> rules = new ArrayList<>(nbt.rules());
            int at = FilterItemData.indexOfRule(rules, added);
            if (at >= 0) rules.set(at, added);
            else rules.add(added);
            return nbt.withRules(rules).withRaw(nbt.rules().isEmpty() ? "" : nbt.raw());
        });
    }

    @Override
    public void removeNbtRule(int entry, int ruleIndex) {
        changeNbt(entry, nbt -> {
            List<NbtCriterion> rules = new ArrayList<>(nbt.rules());
            if (ruleIndex >= 0 && ruleIndex < rules.size()) rules.remove(ruleIndex);
            return nbt.withRules(rules);
        });
    }

    @Override
    public void setNbtRuleValue(int entry, int ruleIndex, String value) {
        Tag parsed;
        try {
            parsed = TagParser.parseTag("{v:" + value + "}").get("v");
        } catch (Exception e) {
            return;
        }
        changeNbt(entry, nbt -> {
            List<NbtCriterion> rules = new ArrayList<>(nbt.rules());
            if (ruleIndex < 0 || ruleIndex >= rules.size()) return nbt;
            NbtCriterion rule = rules.get(ruleIndex);
            rules.set(ruleIndex, new NbtCriterion(rule.path(), rule.operator(), parsed));
            return nbt.withRules(rules);
        });
    }

    @Override
    public void setNbtRaw(int entry, String raw) {
        changeNbt(entry, nbt -> nbt.withRaw(raw));
    }

    @Override
    public void clearNbt(int entry) {
        changeNbt(entry, nbt -> new GeneralFilterEntry.NbtConstraints(List.of(), false, nbt.strict(), ""));
    }
}
