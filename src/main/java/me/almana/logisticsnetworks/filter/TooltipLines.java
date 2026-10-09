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
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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
                return stack.getTooltipLines(context, null, TooltipFlag.NORMAL);
            } catch (LinkageError | RuntimeException e) {
                QUARANTINED.add(item);
                LOGGER.warn("Tooltip of {} is not server-safe; regex filter uses fallback lines: {}",
                        BuiltInRegistries.ITEM.getKey(item), e.toString());
            }
        }
        return fallback(stack, context);
    }

    private static List<Component> fallback(ItemStack stack, Item.TooltipContext context) {
        List<Component> lines = new ArrayList<>();
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (display.hideTooltip())
            return lines;
        lines.add(stack.getHoverName());
        try {
            stack.getItem().appendHoverText(stack, context, display, lines::add, TooltipFlag.NORMAL);
        } catch (LinkageError | RuntimeException ignored) {
            // keeps lines added before throw
        }
        for (TypedDataComponent<?> component : stack.getComponents()) {
            if (!(component.value() instanceof TooltipProvider provider) || !display.shows(component.type()))
                continue;
            try {
                provider.addToTooltip(context, lines::add, TooltipFlag.NORMAL, stack);
            } catch (LinkageError | RuntimeException ignored) {
            }
        }
        if (stack.has(DataComponents.UNBREAKABLE) && display.shows(DataComponents.UNBREAKABLE))
            lines.add(ItemStack.UNBREAKABLE_TOOLTIP);
        return lines;
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
