package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.network.CopyPasteConnectedPayload;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import me.almana.logisticsnetworks.network.MassSelectConnectedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

    @SubscribeEvent
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !Screen.hasControlDown()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.hitResult == null) {
            return;
        }

        InteractionHand hand = event.getHand();
        if (!(player.getItemInHand(hand).getItem() instanceof WrenchItem)) {
            return;
        }

        WrenchItem.Mode mode = WrenchItem.getMode(player.getItemInHand(hand));
        if (mode != WrenchItem.Mode.MASS_PLACEMENT && mode != WrenchItem.Mode.COPY_PASTE) {
            return;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult)
                || blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        if (mode == WrenchItem.Mode.MASS_PLACEMENT) {
            PacketDistributor.sendToServer(new MassSelectConnectedPayload(hand.ordinal(), blockHitResult.getBlockPos()));
        } else {
            PacketDistributor.sendToServer(new CopyPasteConnectedPayload(hand.ordinal(), blockHitResult.getBlockPos()));
        }
        event.setSwingHand(false);
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
