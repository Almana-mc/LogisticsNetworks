package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class LogisticsJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(NodeAttachedComponentProvider.INSTANCE, BlockEntity.class);
        registration.registerEntityDataProvider(LogisticsNodeEntityProvider.INSTANCE, LogisticsNodeEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(NodeAttachedComponentProvider.INSTANCE, Block.class);
        registration.registerEntityComponent(LogisticsNodeEntityProvider.INSTANCE, LogisticsNodeEntity.class);

        registration.hideTarget(Registration.LOGISTICS_NODE.get());

        registration.addRayTraceCallback((hit, accessor, originalAccessor) -> {
            if (hit instanceof EntityHitResult entityHit
                    && entityHit.getEntity() instanceof LogisticsNodeEntity) {
                var base = accessor != null ? accessor : originalAccessor;
                if (base != null) {
                    ItemStack main = base.getPlayer().getMainHandItem();
                    ItemStack off = base.getPlayer().getOffhandItem();
                    if (main.getItem() instanceof WrenchItem || off.getItem() instanceof WrenchItem) {
                        return registration.entityAccessor()
                                .hit(entityHit)
                                .entity(entityHit.getEntity())
                                .level(base.getLevel())
                                .player(base.getPlayer())
                                .serverConnected(base.isServerConnected())
                                .build();
                    }
                }
            }
            return accessor != null ? accessor : originalAccessor;
        });
    }
}
