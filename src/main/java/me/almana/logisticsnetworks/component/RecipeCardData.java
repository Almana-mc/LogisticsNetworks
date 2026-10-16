package me.almana.logisticsnetworks.component;

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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record RecipeCardData(List<Ingredient> ingredients, Optional<StackSnapshot> output, boolean blocking) {

    public static final int MAX_INGREDIENTS = 12;
    public static final int MAX_COUNT = 4096;
    public static final RecipeCardData EMPTY = new RecipeCardData(List.of(), Optional.empty(), false);
    private static final int MAX_ID_LENGTH = 256;
    private static final String KEY_ROOT = "recipe_card";
    private static final String KEY_INGREDIENTS = "ingredients";
    private static final String KEY_OUTPUT = "output";
    private static final String KEY_BLOCKING = "blocking";
    private static final String KEY_ITEM = "item";
    private static final String KEY_TAG = "tag";
    private static final String KEY_COUNT = "count";

    public static final StreamCodec<FriendlyByteBuf, RecipeCardData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> buf.writeNbt(data.save()), buf -> load(buf.readNbt()));

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

    public int setSize() {
        int size = 0;
        for (Ingredient ingredient : ingredients) {
            size += ingredient.count();
        }
        return size;
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
            Ingredient clean = ingredient.withCount(ingredient.count());
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

    private static Optional<ResourceLocation> parseId(String value) {
        return value.isEmpty() || value.length() > MAX_ID_LENGTH ? Optional.empty()
                : Optional.ofNullable(ResourceLocation.tryParse(value));
    }

    private static Optional<Item> itemById(String value) {
        return parseId(value).flatMap(BuiltInRegistries.ITEM::getOptional).filter(item -> item != Items.AIR);
    }

    public record Ingredient(Optional<Item> item, Optional<TagKey<Item>> tag, int count) {

        public static Ingredient ofItem(ItemStack stack, int count) {
            return new Ingredient(Optional.of(stack.getItem()), Optional.empty(), count);
        }

        public static Ingredient ofTag(ResourceLocation tag, int count) {
            return new Ingredient(Optional.empty(), Optional.of(TagKey.create(Registries.ITEM, tag)), count);
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (tag.isPresent()) return stack.is(tag.get());
            return item.isPresent() && stack.is(item.get());
        }

        public ItemStack icon() {
            if (item.isPresent()) return new ItemStack(item.get());
            return tag.flatMap(BuiltInRegistries.ITEM::getTag)
                    .flatMap(set -> set.stream().findFirst())
                    .map(holder -> new ItemStack(holder.value()))
                    .orElse(new ItemStack(Items.BARRIER));
        }

        public Component label() {
            if (tag.isEmpty()) return icon().getHoverName();
            MutableComponent text = Component.literal("#" + tag.get().location());
            return BuiltInRegistries.ITEM.getTag(tag.get()).isPresent() ? text : text.withStyle(ChatFormatting.RED);
        }

        // Anchored item, then its tags
        public Ingredient nextTag() {
            ItemStack base = icon();
            List<TagKey<Item>> tags = base.getTags()
                    .sorted(Comparator.comparing(key -> key.location().toString())).toList();
            if (tags.isEmpty()) return this;
            int at = tag.map(tags::indexOf).orElse(-1);
            Optional<Item> anchor = Optional.of(base.getItem());
            return new Ingredient(anchor, at + 1 < tags.size() ? Optional.of(tags.get(at + 1)) : Optional.empty(), count);
        }

        public Ingredient withCount(int value) {
            return new Ingredient(item, tag, Math.max(1, Math.min(MAX_COUNT, value)));
        }

        boolean valid() {
            if (tag.isPresent()) return true;
            return item.isPresent() && item.get() != Items.AIR;
        }

        boolean sameKey(Ingredient other) {
            if (tag.isPresent() || other.tag.isPresent()) return tag.equals(other.tag);
            return item.equals(other.item);
        }

        CompoundTag save() {
            CompoundTag root = new CompoundTag();
            item.ifPresent(value -> root.putString(KEY_ITEM, BuiltInRegistries.ITEM.getKey(value).toString()));
            tag.ifPresent(value -> root.putString(KEY_TAG, value.location().toString()));
            root.putInt(KEY_COUNT, count);
            return root;
        }

        static Optional<Ingredient> load(CompoundTag root) {
            Optional<Item> item = itemById(root.getString(KEY_ITEM));
            Optional<TagKey<Item>> tag = parseId(root.getString(KEY_TAG)).map(id -> TagKey.create(Registries.ITEM, id));
            if (item.isEmpty() && tag.isEmpty()) return Optional.empty();
            return Optional.of(new Ingredient(item, tag, 1).withCount(root.getInt(KEY_COUNT)));
        }
    }
}
