package me.almana.logisticsnetworks.integration.jade;

import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import me.almana.logisticsnetworks.item.WornWrench;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.EntityHitResult;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class LogisticsJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(NodeAttachedDataProvider.INSTANCE, Block.class);
        registration.entityTypeOperations().hide(Registration.LOGISTICS_NODE.getKey());
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(NodeAttachedComponentProvider.INSTANCE, Block.class);
        registration.registerEntityComponent(LogisticsNodeEntityProvider.INSTANCE, LogisticsNodeEntity.class);

        registration.addRayTraceCallback((hit, accessor, originalAccessor) -> {
            if (hit instanceof EntityHitResult entityHit
                    && entityHit.getEntity() instanceof LogisticsNodeEntity) {
                var base = accessor != null ? accessor : originalAccessor;
                if (base != null) {
                    if (!WornWrench.held(base.getPlayer()).isEmpty()) {
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
