package me.almana.logisticsnetworks.filter;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;



public final class TooltipLines {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_CACHED = 4096;
    private static final int MAX_PENDING = 256;
    private static final Map<Key, List<String>> CACHE = new ConcurrentHashMap<>();
    private static final Set<Key> PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<Item> QUARANTINED = ConcurrentHashMap.newKeySet();

    private TooltipLines() {
    }

    @Nullable
    public static List<String> get(ItemStack stack) {
        Key probe = new Key(stack);
        List<String> cached = CACHE.get(probe);
        if (cached != null)
            return cached;
        if (!ThreadGuard.isServerThread()) {
            if (PENDING.size() < MAX_PENDING)
                PENDING.add(probe.copy());
            return null;
        }
        return compute(probe.copy());
    }

    public static void drainPending() {
        for (Key key : PENDING) {
            PENDING.remove(key);
            if (!CACHE.containsKey(key))
                compute(key);
        }
    }

    public static void clear() {
        CACHE.clear();
        PENDING.clear();
        QUARANTINED.clear();
    }

    private static List<String> compute(Key key) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Item.TooltipContext context = Item.TooltipContext.of(server == null ? null : server.overworld());
        List<String> lines = build(key.stack(), context).stream().map(Component::getString).toList();
        // ponytail: full clear on overflow, LRU if churn shows in profiles
        if (CACHE.size() >= MAX_CACHED)
            CACHE.clear();
        CACHE.put(key, lines);
        return lines;
    }

    private static List<Component> build(ItemStack stack, Item.TooltipContext context) {
        Item item = stack.getItem();
        if (!QUARANTINED.contains(item)) {
            try {
                List<Component> lines = new ArrayList<>(
                        stack.getTooltipLines(context, null, TooltipFlag.ADVANCED));
                appendTags(stack, lines);
                return lines;
            } catch (LinkageError | RuntimeException e) {
                QUARANTINED.add(item);
                LOGGER.warn("Tooltip of {} is not server-safe; regex filter uses fallback lines: {}",
                        BuiltInRegistries.ITEM.getKey(item), e.toString());
            }
        }
        List<Component> lines = fallback(stack, context);
        appendTags(stack, lines);
        return lines;
    }
   
    private static List<Component> fallback(ItemStack stack, Item.TooltipContext context) {
        List<Component> lines = new ArrayList<>();
        if (stack.has(DataComponents.HIDE_TOOLTIP))
            return lines;
        lines.add(stack.getHoverName());
        if (!stack.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
            try {
                stack.getItem().appendHoverText(stack, context, lines, TooltipFlag.ADVANCED);
            } catch (LinkageError | RuntimeException ignored) {
                // keeps lines added before throw
            }
        }
        for (TypedDataComponent<?> component : stack.getComponents()) {
            if (!(component.value() instanceof TooltipProvider provider))
                continue;
            try {
                provider.addToTooltip(context, lines::add, TooltipFlag.ADVANCED);
            } catch (LinkageError | RuntimeException ignored) {
            }
        }
        return lines;
    }

    private static void appendTags(ItemStack stack, List<Component> lines) {
        try {
            Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem());
            holder.tags().forEach(tag -> lines.add(
                    Component.literal("#" + tag.location())));
        } catch (LinkageError | RuntimeException ignored) {
            // tags: supplementary text source for regex matching only. exceptions must not affect the main filter flow.
        }
    }

    private record Key(ItemStack stack) {
        Key copy() {
            return new Key(stack.copyWithCount(1));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && ItemStack.isSameItemSameComponents(stack, other.stack);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }
    }
}
