package me.almana.logisticsnetworks.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class TransferCapabilityCache {

    private record Key(ResourceKey<Level> dim, long pos, @Nullable Direction side, Capability<?> cap) {
    }

    private record Entry(BlockEntity blockEntity, LazyOptional<?> lazy) {
    }

    private final Map<Key, Entry> entries = new HashMap<>();

    @Nullable
    @SuppressWarnings("unchecked")
    <T> T get(ServerLevel level, BlockPos pos, @Nullable Direction side, Capability<T> cap) {
        Key key = new Key(level.dimension(), pos.asLong(), side, cap);
        BlockEntity be = level.getBlockEntity(pos);
        Entry entry = entries.get(key);
        // Stale handlers can void items
        if (entry != null && entry.blockEntity() == be && !be.isRemoved() && entry.lazy().isPresent()) {
            return ((LazyOptional<T>) entry.lazy()).orElse(null);
        }
        if (entry != null) {
            entries.remove(key);
        }
        if (be == null || be.isRemoved()) {
            return null;
        }
        LazyOptional<T> lazy = be.getCapability(cap, side);
        if (!lazy.isPresent()) {
            return null;
        }
        Entry fresh = new Entry(be, lazy);
        entries.put(key, fresh);
        lazy.addListener(invalid -> entries.remove(key, fresh));
        return lazy.orElse(null);
    }

    // Linear scan; evictions are rare
    public void evict(ResourceKey<Level> dim, BlockPos pos) {
        long packed = pos.asLong();
        entries.keySet().removeIf(key -> key.pos() == packed && key.dim().equals(dim));
    }

    public void clear(ResourceKey<Level> dim) {
        entries.keySet().removeIf(key -> key.dim().equals(dim));
    }
}
