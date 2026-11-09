package me.almana.logisticsnetworks.client.lnet;

import me.almana.logisticsnetworks.component.GeneralFilterEntry;
import me.almana.logisticsnetworks.component.LogisticsDataComponents;
import me.almana.logisticsnetworks.component.RecipeCardData;
import me.almana.logisticsnetworks.component.StackSnapshot;
import me.almana.logisticsnetworks.item.RecipeCardItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LnetRecipeText {

    private static final Pattern COUNT = Pattern.compile("(\\d+)(mb)? (.+)");

    private LnetRecipeText() {
    }

    static boolean isCard(GeneralFilterEntry entry) {
        return entry.item() != null && entry.item().item().value() instanceof RecipeCardItem;
    }

    static GeneralFilterEntry cardEntry(ItemStack card, int slot) {
        return GeneralFilterEntry.empty(slot).withItem(StackSnapshot.of(card));
    }

    static ItemStack readCard(String text, HolderLookup.Provider registries) throws IOException {
        List<String> parts = LnetEntryText.split(text, ",");
        boolean blocking = parts.size() > 1 && parts.get(parts.size() - 1).equals("blocking");
        if (blocking) {
            parts = parts.subList(0, parts.size() - 1);
        }
        List<RecipeCardData.Ingredient> ingredients = new ArrayList<>();
        Optional<StackSnapshot> output = Optional.empty();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            int arrow = LnetEntryText.find(part, "->", 0);
            if (arrow >= 0) {
                if (i != parts.size() - 1) {
                    throw new IOException("'->' must come after the last ingredient");
                }
                output = Optional.of(readOutput(part.substring(arrow + 2).trim(), registries));
                part = part.substring(0, arrow).trim();
            }
            ingredients.add(readIngredient(part, registries));
        }
        if (ingredients.isEmpty()) {
            throw new IOException("recipe needs at least one ingredient");
        }
        if (ingredients.size() > RecipeCardData.MAX_INGREDIENTS) {
            throw new IOException("more than " + RecipeCardData.MAX_INGREDIENTS + " ingredients");
        }
        ItemStack card = new ItemStack(Registration.RECIPE_CARD.get());
        card.set(LogisticsDataComponents.RECIPE_CARD.get(), new RecipeCardData(ingredients, output, blocking));
        return RecipeCardItem.stamp(card);
    }

    private static RecipeCardData.Ingredient readIngredient(String text, HolderLookup.Provider registries)
            throws IOException {
        Matcher matcher = COUNT.matcher(text);
        if (!matcher.matches()) {
            throw new IOException("ingredient needs a count: '" + text + "'");
        }
        int count = LnetEntryText.number(matcher.group(1));
        if (count < 1 || count > RecipeCardData.MAX_AMOUNT) {
            throw new IOException("count out of range in '" + text + "'");
        }
        boolean mb = matcher.group(2) != null;
        String rest = matcher.group(3).trim();
        if (rest.startsWith("chemical ")) {
            String id = LnetEntryText.readChemical(rest.substring("chemical ".length()).trim());
            return new RecipeCardData.Ingredient(Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(id), count);
        }
        if (rest.startsWith("fluid ") || mb) {
            String id = LnetEntryText.readFluid(rest.startsWith("fluid ") ? rest.substring("fluid ".length()).trim() : rest);
            return new RecipeCardData.Ingredient(Optional.empty(), Optional.empty(), Optional.of(id),
                    Optional.empty(), count);
        }
        if (count > RecipeCardData.MAX_COUNT) {
            throw new IOException("count out of range in '" + text + "'");
        }
        if (rest.startsWith("#")) {
            return new RecipeCardData.Ingredient(Optional.empty(),
                    Optional.of(TagKey.create(Registries.ITEM, LnetEntryText.id(rest.substring(1), "tag"))),
                    Optional.empty(), Optional.empty(), count);
        }
        return RecipeCardData.Ingredient.ofItem(LnetEntryText.readItem(rest, registries), count);
    }

    private static StackSnapshot readOutput(String text, HolderLookup.Provider registries) throws IOException {
        Matcher matcher = COUNT.matcher(text);
        int count = 1;
        String id = text;
        if (matcher.matches() && matcher.group(2) == null) {
            count = LnetEntryText.number(matcher.group(1));
            id = matcher.group(3).trim();
        }
        ItemStack stack = LnetEntryText.readItem(id, registries);
        if (count < 1 || count > 99) {
            throw new IOException("output count must be 1 to 99");
        }
        return StackSnapshot.of(stack.copyWithCount(count));
    }

    static String writeCard(RecipeCardData card, HolderLookup.Provider registries) {
        List<String> parts = new ArrayList<>();
        for (RecipeCardData.Ingredient ingredient : card.ingredients()) {
            parts.add(writeIngredient(ingredient, registries));
        }
        StringBuilder out = new StringBuilder(String.join(", ", parts));
        card.output().ifPresent(output -> out.append(" -> ").append(output.count()).append(' ')
                .append(LnetEntryText.writeItem(output.toStack().copyWithCount(1), registries)));
        if (card.blocking()) {
            out.append(", blocking");
        }
        return out.toString();
    }

    private static String writeIngredient(RecipeCardData.Ingredient ingredient, HolderLookup.Provider registries) {
        if (ingredient.fluid().isPresent()) {
            return ingredient.count() + "mb " + LnetEntryText.shortId(ingredient.fluid().get());
        }
        if (ingredient.chemical().isPresent()) {
            return ingredient.count() + "mb chemical " + ingredient.chemical().get();
        }
        if (ingredient.tag().isPresent()) {
            return ingredient.count() + " #" + LnetEntryText.shortId(ingredient.tag().get().location().toString());
        }
        return ingredient.count() + " "
                + LnetEntryText.writeItem(ingredient.item().map(StackSnapshot::toStack).orElse(ItemStack.EMPTY),
                registries);
    }
}
