package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
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
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record RecipeCardData(List<Ingredient> ingredients, Optional<StackSnapshot> output, boolean blocking) {

    public static final int MAX_INGREDIENTS = 12;
    public static final int MAX_COUNT = 4096;
    public static final int MAX_AMOUNT = 1_000_000;
    public static final RecipeCardData EMPTY = new RecipeCardData(List.of(), Optional.empty(), false);

    public static final Codec<RecipeCardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.listOf(0, MAX_INGREDIENTS).optionalFieldOf("ingredients", List.of())
                    .forGetter(RecipeCardData::ingredients),
            StackSnapshot.CODEC.optionalFieldOf("output").forGetter(RecipeCardData::output),
            Codec.BOOL.optionalFieldOf("blocking", false).forGetter(RecipeCardData::blocking)
    ).apply(instance, RecipeCardData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeCardData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

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
        return stack.getOrDefault(LogisticsDataComponents.RECIPE_CARD.get(), EMPTY);
    }

    public int setSize() {
        int size = 0;
        for (Ingredient ingredient : ingredients) {
            size += ingredient.count();
        }
        return size;
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

    public record Ingredient(Optional<StackSnapshot> item, Optional<TagKey<Item>> tag, Optional<String> fluid,
            Optional<String> chemical, int count) {

        public static final Codec<Ingredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StackSnapshot.CODEC.optionalFieldOf("item").forGetter(Ingredient::item),
                TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(Ingredient::tag),
                Codec.STRING.optionalFieldOf("fluid").forGetter(Ingredient::fluid),
                Codec.STRING.optionalFieldOf("chemical").forGetter(Ingredient::chemical),
                Codec.intRange(1, MAX_AMOUNT).fieldOf("count").forGetter(Ingredient::count)
        ).apply(instance, Ingredient::new));

        public static Ingredient ofItem(ItemStack stack, int count) {
            return new Ingredient(Optional.of(StackSnapshot.of(stack.copyWithCount(1))), Optional.empty(),
                    Optional.empty(), Optional.empty(), count);
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
            return item.isPresent() && stack.is(item.get().item());
        }

        public boolean matches(FluidStack stack) {
            return !stack.isEmpty()
                    && fluid.filter(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString()::equals).isPresent();
        }

        public FluidStack fluidStack(int amount) {
            return fluid.map(ResourceLocation::tryParse)
                    .flatMap(id -> BuiltInRegistries.FLUID.getOptional(id))
                    .map(value -> new FluidStack(value, amount))
                    .orElse(FluidStack.EMPTY);
        }

        public ItemStack icon() {
            if (item.isPresent()) return item.get().toStack();
            return tag.flatMap(BuiltInRegistries.ITEM::getTag)
                    .flatMap(set -> set.stream().findFirst())
                    .map(ItemStack::new)
                    .orElse(new ItemStack(Items.BARRIER));
        }

        public Component label() {
            if (kind() == Kind.FLUID) return fluidStack(1).getHoverName();
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
            Optional<StackSnapshot> anchor = Optional.of(StackSnapshot.of(base.copyWithCount(1)));
            return new Ingredient(anchor, at + 1 < tags.size() ? Optional.of(tags.get(at + 1)) : Optional.empty(),
                    Optional.empty(), Optional.empty(), count);
        }

        public Ingredient withCount(int value) {
            int max = kind() == Kind.ITEM ? MAX_COUNT : MAX_AMOUNT;
            return new Ingredient(item, tag, fluid, chemical, Math.max(1, Math.min(max, value)));
        }

        boolean valid() {
            int kinds = (item.isPresent() || tag.isPresent() ? 1 : 0) + (fluid.isPresent() ? 1 : 0)
                    + (chemical.isPresent() ? 1 : 0);
            if (kinds != 1) return false;
            if (fluid.isPresent()) return !fluidStack(1).isEmpty();
            if (chemical.isPresent()) return !chemical.get().isBlank();
            if (tag.isPresent()) return true;
            return item.get().item().value() != Items.AIR;
        }

        Ingredient stripped() {
            Optional<StackSnapshot> clean = item.map(snapshot ->
                    new StackSnapshot(snapshot.item(), 1, DataComponentPatch.EMPTY));
            return new Ingredient(clean, tag, fluid, chemical, count).withCount(count);
        }

        boolean sameKey(Ingredient other) {
            if (kind() != other.kind()) return false;
            if (tag.isPresent() || other.tag.isPresent()) return tag.equals(other.tag);
            if (kind() != Kind.ITEM) return fluid.equals(other.fluid) && chemical.equals(other.chemical);
            return item.map(StackSnapshot::item).equals(other.item.map(StackSnapshot::item));
        }
    }
}
