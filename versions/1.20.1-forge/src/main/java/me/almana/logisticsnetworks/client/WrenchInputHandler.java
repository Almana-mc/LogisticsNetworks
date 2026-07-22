package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.network.NetworkHandler;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.screen.WrenchColorScreen;
import me.almana.logisticsnetworks.network.CopyPasteConnectedPayload;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import me.almana.logisticsnetworks.network.MassSelectConnectedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public class WrenchInputHandler {

    public static final KeyMapping OPEN_COLOR_EDITOR = new KeyMapping(
            "key.logisticsnetworks.wrench_colors",
            InputConstants.KEY_G,
            "key.categories.logisticsnetworks");

    @EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_COLOR_EDITOR);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!OPEN_COLOR_EDITOR.consumeClick()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            return;
        }
        InteractionHand hand = findWrenchHand(player);
        if (hand != null) {
            minecraft.setScreen(new WrenchColorScreen(player.getItemInHand(hand), hand));
        }
    }

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

        double scrollDelta = event.getScrollDelta();
        if (scrollDelta == 0.0D) {
            return;
        }

        InteractionHand hand = findWrenchHand(player);
        if (hand == null) {
            return;
        }

        NetworkHandler.sendToServer(new CycleWrenchModePayload(hand.ordinal(), scrollDelta > 0.0D));
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
            NetworkHandler.sendToServer(new MassSelectConnectedPayload(hand.ordinal(), blockHitResult.getBlockPos()));
        } else {
            NetworkHandler.sendToServer(new CopyPasteConnectedPayload(hand.ordinal(), blockHitResult.getBlockPos()));
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @Nullable
    public static InteractionHand findWrenchHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof WrenchItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof WrenchItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
