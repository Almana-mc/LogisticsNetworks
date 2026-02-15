package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public class WrenchInputHandler {

    @SubscribeEvent
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            return;
        }

        if (event.getScrollDeltaY() == 0.0D) {
            return;
        }

        InteractionHand hand = findWrenchHand(player);
        if (hand == null) {
            return;
        }

        PacketDistributor.sendToServer(new CycleWrenchModePayload(hand.ordinal(), event.getScrollDeltaY() > 0.0D));
        event.setCanceled(true);
    }

    @Nullable
    private static InteractionHand findWrenchHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof WrenchItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof WrenchItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
