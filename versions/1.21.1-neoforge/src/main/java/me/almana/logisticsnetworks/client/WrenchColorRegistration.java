package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.registration.Registration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public final class WrenchColorRegistration {

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // renderer reads tint alpha
        event.register((stack, tintIndex) -> 0xFF000000 | switch (tintIndex) {
            case 0 -> WrenchItem.getCaseColor(stack);
            case 1 -> WrenchItem.getScreenColor(stack);
            default -> 0xFFFFFF;
        }, Registration.WRENCH.get());
    }

    private WrenchColorRegistration() {
    }
}
