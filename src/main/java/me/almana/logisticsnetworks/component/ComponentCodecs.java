package me.almana.logisticsnetworks.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

public final class ComponentCodecs {

    public static final Codec<Tag> TAG = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue().copy(),
            tag -> new Dynamic<>(NbtOps.INSTANCE, tag.copy()));

    private ComponentCodecs() {
    }
}
