package me.almana.logisticsnetworks.filter;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TooltipLines {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_CACHED = 4096;
    private static final Map<Key, List<String>> CACHE = new ConcurrentHashMap<>();
    private static final Set<Item> QUARANTINED = ConcurrentHashMap.newKeySet();

    private TooltipLines() {
    }

    public static List<String> get(ItemStack stack) {
        Key probe = new Key(stack);
        List<String> cached = CACHE.get(probe);
        if (cached != null)
            return cached;
        List<String> lines = build(stack).stream().map(Component::getString).toList();
        // ponytail: full clear, not LRU
        if (CACHE.size() >= MAX_CACHED)
            CACHE.clear();
        CACHE.put(probe.copy(), lines);
        return lines;
    }

    public static void clear() {
        CACHE.clear();
        QUARANTINED.clear();
    }

    private static List<Component> build(ItemStack stack) {
        Item item = stack.getItem();
        if (!QUARANTINED.contains(item)) {
            try {
                return stack.getTooltipLines(null, TooltipFlag.NORMAL);
            } catch (LinkageError | RuntimeException e) {
                // Client-only tooltip code
                QUARANTINED.add(item);
                LOGGER.warn("Tooltip of {} is not server-safe; regex filter uses fallback lines: {}",
                        BuiltInRegistries.ITEM.getKey(item), e.toString());
            }
        }
        return fallback(stack);
    }

    private static List<Component> fallback(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        int hidden = stack.hasTag() ? stack.getTag().getInt("HideFlags") : 0;
        if ((hidden & ItemStack.TooltipPart.ADDITIONAL.getMask()) == 0) {
            try {
                stack.getItem().appendHoverText(stack, null, lines, TooltipFlag.NORMAL);
            } catch (LinkageError | RuntimeException ignored) {
                // keeps lines added before throw
            }
        }
        if ((hidden & ItemStack.TooltipPart.ENCHANTMENTS.getMask()) == 0)
            ItemStack.appendEnchantmentNames(lines, stack.getEnchantmentTags());
        appendLore(stack, lines);
        return lines;
    }

    private static void appendLore(ItemStack stack, List<Component> lines) {
        CompoundTag display = stack.getTagElement("display");
        if (display == null)
            return;
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        for (int i = 0; i < lore.size(); i++) {
            try {
                Component line = Component.Serializer.fromJson(lore.getString(i));
                if (line != null)
                    lines.add(line);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private record Key(ItemStack stack) {
        Key copy() {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            return new Key(copy);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && ItemStack.isSameItemSameTags(stack, other.stack);
        }

        @Override
        public int hashCode() {
            return 31 * stack.getItem().hashCode() + (stack.hasTag() ? stack.getTag().hashCode() : 0);
        }
    }
}
