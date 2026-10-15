package me.almana.logisticsnetworks.integration.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;

public record StorageLink(StorageBackend backend, GlobalPos position) {

    public static final Codec<StorageLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StorageBackend.CODEC.fieldOf("backend").forGetter(StorageLink::backend),
            GlobalPos.CODEC.fieldOf("position").forGetter(StorageLink::position)
    ).apply(instance, StorageLink::new));
}
