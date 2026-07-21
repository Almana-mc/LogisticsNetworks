package me.almana.logisticsnetworks.network.payload;

import net.minecraft.world.entity.player.Player;

public interface IPayloadContext {

    Player player();

    void enqueueWork(Runnable work);
}
