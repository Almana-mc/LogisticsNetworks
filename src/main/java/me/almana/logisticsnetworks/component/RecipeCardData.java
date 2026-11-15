package me.almana.logisticsnetworks.component;

import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.filter.SlotExpressionUtil;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import me.almana.logisticsnetworks.network.codec.StreamCodec;
import me.almana.logisticsnetworks.util.ItemDataUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record RecipeCardData(List<Ingredient> ingredients, Optional<StackSnapshot> output, boolean blocking) {

    public static final int MAX_INGREDIENTS = 12;
    public static final int MAX_COUNT = 4096;
    public static final int MAX_AMOUNT = 1_000_000;
    public static final RecipeCardData EMPTY = new RecipeCardData(List.of(), Optional.empty(), false);
    private static final int MAX_ID_LENGTH = 256;
    private static final String KEY_ROOT = "recipe_card";
    private static final String KEY_INGREDIENTS = "ingredients";
    private static final String KEY_OUTPUT = "output";
    private static final String KEY_BLOCKING = "blocking";
    private static final String KEY_ITEM = "item";
    private static final String KEY_TAG = "tag";
    private static final String KEY_FLUID = "fluid";
    private static final String KEY_CHEMICAL = "chemical";
    private static final String KEY_COUNT = "count";
    private static final String KEY_SLOTS = "slots";
    private static final String KEY_SPLIT = "split";

    public static final StreamCodec<FriendlyByteBuf, RecipeCardData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> buf.writeNbt(data.save()), buf -> load(buf.readNbt()));

    public enum Kind {
        ITEM(ChannelType.ITEM),
        FLUID(ChannelType.FLUID),
        CHEMICAL(ChannelType.CHEMICAL);

        public final ChannelType channel;

        Kind(ChannelType channel) {
            this.channel = channel;
        }
    }

    public RecipeCardData {
        ingredients = List.copyOf(ingredients);
    }

    public static RecipeCardData of(ItemStack stack) {
        CompoundTag custom = ItemDataUtil.peekCustomData(stack);
        return custom.contains(KEY_ROOT, Tag.TAG_COMPOUND) ? load(custom.getCompound(KEY_ROOT)) : EMPTY;
    }

    public static boolean has(ItemStack stack) {
        return ItemDataUtil.peekCustomData(stack).contains(KEY_ROOT, Tag.TAG_COMPOUND);
    }

    public static void set(ItemStack stack, RecipeCardData data) {
        ItemDataUtil.updateCustomData(stack, custom -> custom.put(KEY_ROOT, data.save()));
    }

    public static void clear(ItemStack stack) {
        ItemDataUtil.updateCustomData(stack, custom -> custom.remove(KEY_ROOT));
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        ingredients.forEach(ingredient -> list.add(ingredient.save()));
        root.put(KEY_INGREDIENTS, list);
        output.ifPresent(snapshot -> root.put(KEY_OUTPUT, snapshot.tag().copy()));
        if (blocking) root.putBoolean(KEY_BLOCKING, true);
        return root;
    }

    // Untrusted NBT, bounded read
    public static RecipeCardData load(@Nullable CompoundTag root) {
        if (root == null) return EMPTY;
        ListTag tags = root.getList(KEY_INGREDIENTS, Tag.TAG_COMPOUND);
        List<Ingredient> list = new ArrayList<>();
        for (int i = 0; i < Math.min(tags.size(), MAX_INGREDIENTS); i++) {
            Ingredient.load(tags.getCompound(i)).ifPresent(list::add);
        }
        CompoundTag out = root.getCompound(KEY_OUTPUT);
        Optional<StackSnapshot> output = itemById(out.getString("id"))
                .map(value -> snapshot(value, out.getByte("Count")));
        return new RecipeCardData(list, output, root.getBoolean(KEY_BLOCKING)).sanitized();
    }

    public long amount(Kind kind) {
        long total = 0;
        for (Ingredient ingredient : ingredients) {
            if (ingredient.kind() == kind) total += ingredient.count();
        }
        return total;
    }

    public RecipeCardData withIngredient(int index, Ingredient value) {
        List<Ingredient> list = new ArrayList<>(ingredients);
        if (index < list.size()) {
            list.set(index, value);
        } else {
            list.add(value);
        }
        return new RecipeCardData(list, output, blocking).sanitized();
    }

    public RecipeCardData withoutIngredient(int index) {
        List<Ingredient> list = new ArrayList<>(ingredients);
        if (index < list.size()) {
            list.remove(index);
        }
        return new RecipeCardData(list, output, blocking);
    }

    public RecipeCardData withOutput(Optional<StackSnapshot> value) {
        return new RecipeCardData(ingredients, value, blocking).sanitized();
    }

    public RecipeCardData withBlocking(boolean value) {
        return new RecipeCardData(ingredients, output, value);
    }

    public RecipeCardData sanitized() {
        List<Ingredient> merged = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.valid()) continue;
            Ingredient clean = ingredient.stripped();
            int at = indexOfKey(merged, clean);
            if (at >= 0) {
                merged.set(at, merged.get(at).withCount(merged.get(at).count() + clean.count()));
            } else if (merged.size() < MAX_INGREDIENTS) {
                merged.add(clean);
            }
        }
        Optional<StackSnapshot> cleanOutput = output.map(StackSnapshot::toStack)
                .filter(stack -> !stack.isEmpty())
                .map(stack -> snapshot(stack.getItem(), stack.getCount()));
        return new RecipeCardData(merged, cleanOutput, blocking);
    }

    private static int indexOfKey(List<Ingredient> list, Ingredient key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).sameKey(key)) return i;
        }
        return -1;
    }

    // Item id and count only
    private static StackSnapshot snapshot(Item item, int count) {
        return StackSnapshot.of(new ItemStack(item, Math.max(1, Math.min(64, count))));
    }

    private static Optional<String> boundedId(String value) {
        return value.isEmpty() || value.length() > MAX_ID_LENGTH ? Optional.empty() : Optional.of(value);
    }

    private static Optional<ResourceLocation> parseId(String value) {
        return boundedId(value).map(ResourceLocation::tryParse);
    }

    private static Optional<Item> itemById(String value) {
        return parseId(value).flatMap(BuiltInRegistries.ITEM::getOptional).filter(item -> item != Items.AIR);
    }

    public record Ingredient(Optional<Item> item, Optional<TagKey<Item>> tag, Optional<String> fluid,
            Optional<String> chemical, int count, List<Integer> slots, boolean split) {

        public Ingredient {
            slots = List.copyOf(slots);
        }

        public Ingredient(Optional<Item> item, Optional<TagKey<Item>> tag, Optional<String> fluid,
                Optional<String> chemical, int count) {
            this(item, tag, fluid, chemical, count, List.of(), false);
        }

        public static Ingredient ofItem(ItemStack stack, int count) {
            return new Ingredient(Optional.of(stack.getItem()), Optional.empty(), Optional.empty(), Optional.empty(),
                    count);
        }

        public static Ingredient ofTag(ResourceLocation tag, int count) {
            return new Ingredient(Optional.empty(), Optional.of(TagKey.create(Registries.ITEM, tag)),
                    Optional.empty(), Optional.empty(), count);
        }

        public static Ingredient ofFluid(FluidStack stack) {
            return new Ingredient(Optional.empty(), Optional.empty(),
                    Optional.of(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString()), Optional.empty(),
                    clamp(stack.getAmount()));
        }

        public static Ingredient ofChemical(String id, long amount) {
            return new Ingredient(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(id),
                    clamp(amount));
        }

        private static int clamp(long amount) {
            return (int) Math.max(1, Math.min(MAX_AMOUNT, amount));
        }

        public Kind kind() {
            if (fluid.isPresent()) return Kind.FLUID;
            return chemical.isPresent() ? Kind.CHEMICAL : Kind.ITEM;
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (tag.isPresent()) return stack.is(tag.get());
            return item.isPresent() && stack.is(item.get());
        }

        public boolean matches(FluidStack stack) {
            return fluid.isPresent() && !stack.isEmpty()
                    && fluid.get().equals(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
        }

        public FluidStack fluidStack(int amount) {
            return fluid.map(ResourceLocation::tryParse)
                    .flatMap(id -> BuiltInRegistries.FLUID.getOptional(id))
                    .map(value -> new FluidStack(value, amount))
                    .orElse(FluidStack.EMPTY);
        }

        public ItemStack icon() {
            if (item.isPresent()) return new ItemStack(item.get());
            return tag.flatMap(BuiltInRegistries.ITEM::getTag)
                    .flatMap(set -> set.stream().findFirst())
                    .map(holder -> new ItemStack(holder.value()))
                    .orElse(new ItemStack(Items.BARRIER));
        }

        public Component label() {
            if (kind() == Kind.FLUID) return fluidStack(1).getDisplayName();
            if (kind() == Kind.CHEMICAL) {
                Component name = MekanismCompat.getChemicalTextComponent(chemical.get());
                return name != null ? name : Component.literal(chemical.get()).withStyle(ChatFormatting.RED);
            }
            if (tag.isEmpty()) return icon().getHoverName();
            MutableComponent text = Component.literal("#" + tag.get().location());
            return BuiltInRegistries.ITEM.getTag(tag.get()).isPresent() ? text : text.withStyle(ChatFormatting.RED);
        }

        public String amountText() {
            return kind() == Kind.ITEM ? count + "×" : count + " mB";
        }

        // Anchored item, then its tags
        public Ingredient nextTag() {
            if (kind() != Kind.ITEM) return this;
            ItemStack base = icon();
            List<TagKey<Item>> tags = base.getTags()
                    .sorted(Comparator.comparing(key -> key.location().toString())).toList();
            if (tags.isEmpty()) return this;
            int at = tag.map(tags::indexOf).orElse(-1);
            Optional<Item> anchor = Optional.of(base.getItem());
            return new Ingredient(anchor, at + 1 < tags.size() ? Optional.of(tags.get(at + 1)) : Optional.empty(),
                    Optional.empty(), Optional.empty(), count, slots, split);
        }

        public Ingredient withCount(int value) {
            int max = kind() == Kind.ITEM ? MAX_COUNT : MAX_AMOUNT;
            return new Ingredient(item, tag, fluid, chemical, Math.max(1, Math.min(max, value)), slots, split);
        }

        public Ingredient withSlots(List<Integer> value) {
            return new Ingredient(item, tag, fluid, chemical, count, value, split);
        }

        public Ingredient withSplit(boolean value) {
            return new Ingredient(item, tag, fluid, chemical, count, slots, value);
        }

        public MutableComponent slotsLabel() {
            if (slots.isEmpty()) return Component.translatable("tooltip.logisticsnetworks.recipe_card.slots_any");
            return Component.translatable(split ? "tooltip.logisticsnetworks.recipe_card.slots_split"
                    : "tooltip.logisticsnetworks.recipe_card.slots", SlotExpressionUtil.formatSlots(slots));
        }

        boolean valid() {
            int kinds = (item.isPresent() || tag.isPresent() ? 1 : 0) + (fluid.isPresent() ? 1 : 0)
                    + (chemical.isPresent() ? 1 : 0);
            if (kinds != 1) return false;
            if (fluid.isPresent()) return !fluidStack(1).isEmpty();
            if (chemical.isPresent()) {
                String id = chemical.get();
                return ResourceLocation.tryParse(id.substring(id.indexOf('|') + 1)) != null;
            }
            if (tag.isPresent()) return true;
            return item.get() != Items.AIR;
        }

        Ingredient stripped() {
            List<Integer> cleanSlots = kind() == Kind.ITEM ? slots.stream().distinct().sorted().toList() : List.of();
            return new Ingredient(item, tag, fluid, chemical, count, cleanSlots,
                    split && !cleanSlots.isEmpty()).withCount(count);
        }

        boolean sameKey(Ingredient other) {
            if (kind() != other.kind() || !slots.equals(other.slots) || split != other.split) return false;
            if (tag.isPresent() || other.tag.isPresent()) return tag.equals(other.tag);
            if (kind() != Kind.ITEM) return fluid.equals(other.fluid) && chemical.equals(other.chemical);
            return item.equals(other.item);
        }

        CompoundTag save() {
            CompoundTag root = new CompoundTag();
            item.ifPresent(value -> root.putString(KEY_ITEM, BuiltInRegistries.ITEM.getKey(value).toString()));
            tag.ifPresent(value -> root.putString(KEY_TAG, value.location().toString()));
            fluid.ifPresent(value -> root.putString(KEY_FLUID, value));
            chemical.ifPresent(value -> root.putString(KEY_CHEMICAL, value));
            root.putInt(KEY_COUNT, count);
            if (!slots.isEmpty()) root.putIntArray(KEY_SLOTS, slots);
            if (split) root.putBoolean(KEY_SPLIT, true);
            return root;
        }

        static Optional<Ingredient> load(CompoundTag root) {
            Optional<Item> item = itemById(root.getString(KEY_ITEM));
            Optional<TagKey<Item>> tag = parseId(root.getString(KEY_TAG)).map(id -> TagKey.create(Registries.ITEM, id));
            Optional<String> fluid = parseId(root.getString(KEY_FLUID)).map(ResourceLocation::toString);
            Optional<String> chemical = boundedId(root.getString(KEY_CHEMICAL)).map(Ingredient::cleanChemical);
            if (item.isEmpty() && tag.isEmpty() && fluid.isEmpty() && chemical.isEmpty()) return Optional.empty();
            List<Integer> slots = Arrays.stream(root.getIntArray(KEY_SLOTS))
                    .filter(slot -> slot >= SlotExpressionUtil.MIN_SLOT && slot <= SlotExpressionUtil.MAX_SLOT)
                    .distinct().boxed().toList();
            return Optional.of(new Ingredient(item, tag, fluid, chemical, 1, slots, root.getBoolean(KEY_SPLIT))
                    .withCount(root.getInt(KEY_COUNT)));
        }

        // Keep kind prefix
        private static String cleanChemical(String id) {
            int at = id.indexOf('|') + 1;
            ResourceLocation clean = ResourceLocation.tryParse(id.substring(at));
            return clean == null ? id : id.substring(0, at) + clean;
        }
    }
}
