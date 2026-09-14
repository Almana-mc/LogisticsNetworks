package me.almana.logisticsnetworks.integration.storage;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum StorageBackend implements StringRepresentable {
    AE2("ae2", "ae2"),
    REFINED_STORAGE("refined_storage", "refinedstorage");

    public static final Codec<StorageBackend> CODEC = StringRepresentable.fromEnum(StorageBackend::values);

    private final String serializedName;
    private final String modId;

    StorageBackend(String serializedName, String modId) {
        this.serializedName = serializedName;
        this.modId = modId;
    }

    public String modId() {
        return modId;
    }

    public Component displayName() {
        return Component.translatable("storage.logisticsnetworks." + serializedName);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
