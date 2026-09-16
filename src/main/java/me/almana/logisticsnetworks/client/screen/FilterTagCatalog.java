package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.filter.FilterTargetType;
import me.almana.logisticsnetworks.integration.mekanism.MekanismCompat;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class FilterTagCatalog {
    record Member(ResourceLocation id, String name, String searchText) {
        Member(ResourceLocation id, String name) {
            this(id, name, normalize(id + " " + name));
        }
    }

    record Entry(ResourceLocation id, String name, List<Member> members, boolean related, String searchText) {
        Entry(ResourceLocation id, String name, List<Member> members, boolean related) {
            this(id, name, members, related, normalize(id + " " + name));
        }
    }

    static List<Entry> load(FilterTargetType target, ResourceLocation current, String selected) {
        List<Entry> entries = switch (target) {
            case ITEMS -> fromRegistry(BuiltInRegistries.ITEM, Item::getDescription, "item", current);
            case FLUIDS -> fromRegistry(BuiltInRegistries.FLUID,
                    fluid -> new FluidStack(fluid, 1000).getHoverName(), "fluid", current);
            case CHEMICALS -> MekanismCompat.isLoaded() ? ChemicalTags.load(current) : new ArrayList<>();
        };
        if (current == null && selected != null && entries.stream().noneMatch(entry -> entry.id().toString().equals(selected))) {
            ResourceLocation id = ResourceLocation.tryParse(selected);
            if (id != null) entries.add(new Entry(id, readableName(id), List.of(), false));
        }
        return entries;
    }

    private static <T> List<Entry> fromRegistry(Registry<T> registry, Function<T, Component> names,
                                               String type, ResourceLocation current) {
        Map<T, Member> members = new IdentityHashMap<>();
        List<Entry> entries = new ArrayList<>();
        registry.getTags().forEach(pair -> {
            List<Member> contents = pair.getSecond().stream().map(holder ->
                    members.computeIfAbsent(holder.value(), value ->
                            new Member(registry.getKey(value), names.apply(value).getString()))).toList();
            if (contents.isEmpty()) return;
            boolean related = contents.stream().anyMatch(member -> member.id().equals(current));
            if (current != null && !related) return;
            ResourceLocation id = pair.getFirst().location();
            String key = "tag." + type + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
            String name = I18n.exists(key) ? I18n.get(key) : readableName(id);
            entries.add(new Entry(id, name, contents, related));
        });
        return entries;
    }

    static List<Entry> search(List<Entry> entries, String query, boolean byCount, boolean descending) {
        List<String> words = Arrays.stream(normalize(query).split("\\s+")).filter(word -> !word.isEmpty()).toList();
        Comparator<Entry> alphabetical = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.id().toString());
        Comparator<Entry> order = byCount ? Comparator.comparingInt(entry -> entry.members().size()) : alphabetical;
        if (descending) order = order.reversed();
        return entries.stream().filter(entry -> matches(entry.searchText(), words)
                        || entry.members().stream().anyMatch(member -> words.stream().allMatch(word ->
                        entry.searchText().contains(word) || member.searchText().contains(word))))
                .sorted(order.thenComparing(alphabetical))
                .toList();
    }

    private static boolean matches(String text, List<String> words) {
        return words.stream().allMatch(text::contains);
    }

    static List<Member> searchMembers(List<Member> members, String query) {
        List<String> words = Arrays.stream(normalize(query).split("\\s+")).filter(word -> !word.isEmpty()).toList();
        return members.stream().filter(member -> matches(member.searchText(), words))
                .sorted(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(member -> member.id().toString()))
                .toList();
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[#:_/]+", " ").trim();
    }

    static String readableName(ResourceLocation id) {
        return Arrays.stream(id.getPath().split("/"))
                .map(part -> Arrays.stream(part.split("_"))
                        .filter(word -> !word.isEmpty())
                        .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                        .collect(Collectors.joining(" ")))
                .collect(Collectors.joining(" / "));
    }

    private static final class ChemicalTags {
        static List<Entry> load(ResourceLocation current) {
            return fromRegistry(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY,
                    mekanism.api.chemical.Chemical::getTextComponent, "chemical", current);
        }
    }
}
