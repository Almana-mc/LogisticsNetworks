package me.almana.logisticsnetworks.utils;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import net.minecraft.world.item.Item;

public class HashUtils {
    public static final Hash.Strategy<Item> ITEM_STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(Item o) {
            return System.identityHashCode(o);
        }

        @Override
        public boolean equals(Item a, Item b) {
            return a == b;
        }
    };

    public static Object2IntMap<Item> itemToIntMap() {
        return new Object2IntOpenCustomHashMap<>(ITEM_STRATEGY);
    }

    public static Object2LongMap<Item> itemToLongMap() {
        return new Object2LongOpenCustomHashMap<>(ITEM_STRATEGY);
    }
}
