package me.almana.logisticsnetworks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;

public final class ClientRegistries {

    private ClientRegistries() {
    }

    @Nullable
    public static HolderLookup.Provider onClientThread() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.isSameThread() && minecraft.level != null ? minecraft.level.registryAccess() : null;
    }
}
