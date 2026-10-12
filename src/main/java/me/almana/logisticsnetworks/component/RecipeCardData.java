package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record RecipeCardData(List<Ingredient> ingredients, Optional<StackSnapshot> output, boolean blocking) {

    public static final int MAX_INGREDIENTS = 12;
    public static final int MAX_COUNT = 4096;
    public static final RecipeCardData EMPTY = new RecipeCardData(List.of(), Optional.empty(), false);

    public static final Codec<RecipeCardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(RecipeCardData::ingredients),
            StackSnapshot.CODEC.optionalFieldOf("output").forGetter(RecipeCardData::output),
            Codec.BOOL.optionalFieldOf("blocking", false).forGetter(RecipeCardData::blocking)
    ).apply(instance, RecipeCardData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeCardData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public RecipeCardData {
        ingredients = List.copyOf(ingredients);
    }

    public static RecipeCardData of(ItemStack stack) {
        return stack.getOrDefault(LogisticsDataComponents.RECIPE_CARD.get(), EMPTY);
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
            Ingredient clean = ingredient.stripped();
            int at = indexOfKey(merged, clean);
            if (at >= 0) {
                merged.set(at, merged.get(at).withCount(merged.get(at).count() + clean.count()));
            } else if (merged.size() < MAX_INGREDIENTS) {
                merged.add(clean);
            }
        }
        Optional<StackSnapshot> cleanOutput = output
                .filter(snapshot -> snapshot.item().value() != Items.AIR)
                .map(snapshot -> new StackSnapshot(snapshot.item(), snapshot.count(), DataComponentPatch.EMPTY));
        return new RecipeCardData(merged, cleanOutput, blocking);
    }

    private static int indexOfKey(List<Ingredient> list, Ingredient key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).sameKey(key)) return i;
        }
        return -1;
    }

    public record Ingredient(Optional<StackSnapshot> item, Optional<String> tag, int count) {

        public static final Codec<Ingredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StackSnapshot.CODEC.optionalFieldOf("item").forGetter(Ingredient::item),
                Codec.STRING.optionalFieldOf("tag").forGetter(Ingredient::tag),
                Codec.intRange(1, MAX_COUNT).fieldOf("count").forGetter(Ingredient::count)
        ).apply(instance, Ingredient::new));

        public static Ingredient ofItem(ItemStack stack, int count) {
            return new Ingredient(Optional.of(StackSnapshot.of(stack.copyWithCount(1))), Optional.empty(), count);
        }

        public static Ingredient ofTag(ResourceLocation tag, int count) {
            return new Ingredient(Optional.empty(), Optional.of(tag.toString()), count);
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (tag.isPresent()) return tagKey().map(stack::is).orElse(false);
            return item.isPresent() && stack.is(item.get().item());
        }

        public Optional<TagKey<Item>> tagKey() {
            return tag.map(ResourceLocation::tryParse).map(id -> TagKey.create(Registries.ITEM, id));
        }

        public ItemStack icon() {
            if (item.isPresent()) return item.get().toStack();
            return tagKey().flatMap(BuiltInRegistries.ITEM::getTag)
                    .flatMap(set -> set.stream().findFirst())
                    .map(ItemStack::new)
                    .orElse(new ItemStack(Items.BARRIER));
        }

        public Component label() {
            if (item.isPresent()) return item.get().toStack().getHoverName();
            MutableComponent text = Component.literal("#" + tag.orElse(""));
            boolean known = tagKey().flatMap(BuiltInRegistries.ITEM::getTag).isPresent();
            return known ? text : text.withStyle(ChatFormatting.RED);
        }

        // Exact item, then each tag
        public Ingredient nextTag() {
            ItemStack base = icon();
            List<ResourceLocation> tags = base.getTags().map(TagKey::location)
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
            int at = tag.map(ResourceLocation::tryParse).map(tags::indexOf).orElse(-1);
            if (at + 1 < tags.size()) return ofTag(tags.get(at + 1), count);
            return ofItem(base, count);
        }

        public Ingredient withCount(int value) {
            return new Ingredient(item, tag, Math.max(1, Math.min(MAX_COUNT, value)));
        }

        boolean valid() {
            if (item.isPresent() == tag.isPresent()) return false;
            if (tag.isPresent()) return tagKey().isPresent();
            return item.get().item().value() != Items.AIR;
        }

        Ingredient stripped() {
            Ingredient clean = item.map(snapshot -> new Ingredient(
                    Optional.of(new StackSnapshot(snapshot.item(), 1, DataComponentPatch.EMPTY)), Optional.empty(), count))
                    .orElse(this);
            return clean.withCount(count);
        }

        boolean sameKey(Ingredient other) {
            return tag.equals(other.tag)
                    && item.map(StackSnapshot::item).equals(other.item.map(StackSnapshot::item));
        }
    }
}
