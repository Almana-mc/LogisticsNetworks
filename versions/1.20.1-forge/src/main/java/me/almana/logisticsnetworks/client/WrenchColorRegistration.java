package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WrenchColorRegistration {

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> switch (tintIndex) {
            case 0 -> WrenchItem.getCaseColor(stack);
            case 1 -> WrenchItem.getScreenColor(stack);
            default -> 0xFFFFFF;
        }, Registration.WRENCH.get());
    }

    private WrenchColorRegistration() {
    }
}
