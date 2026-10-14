package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.LogisticsNodeItem;
import me.almana.logisticsnetworks.item.PatternSetterItem;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.CopyPasteConnectedPayload;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import me.almana.logisticsnetworks.network.MassSelectConnectedPayload;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.SyncModifierKeysPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import me.almana.logisticsnetworks.client.screen.NodeGraphScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public class WrenchInputHandler {
    private static int lastSentModifierMask = -1;
    private static boolean forwardedUse;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetInputState();
            return;
        }

        int modifierMask = ClientControls.modifierMask();
        if (modifierMask != lastSentModifierMask) {
            NetworkHandler.sendToServer(new SyncModifierKeysPayload(modifierMask));
            lastSentModifierMask = modifierMask;
        }

        if (minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }

        while (ClientControls.SECONDARY_INTERACTION.consumeClick()) {
            if (ClientControls.usesVanillaUseInput(minecraft.options) || !isLogisticsInteraction(minecraft)) {
                continue;
            }
            forwardedUse = true;
            try {
                minecraft.startUseItem();
            } finally {
                forwardedUse = false;
            }
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetInputState();
        NodeGraphScreen.clearSession();
    }

    @SubscribeEvent
    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        if (!ClientControls.modifier1Down()) {
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
        if (!event.isUseItem()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        boolean sameInput = ClientControls.usesVanillaUseInput(minecraft.options);
        if (isLogisticsInteraction(minecraft) && !sameInput && !forwardedUse) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        if (ClientControls.modifier2Down() && (sameInput || forwardedUse)) {
            tryConnectedPaste(event, minecraft, player);
        }
    }

    private static void tryConnectedPaste(InputEvent.InteractionKeyMappingTriggered event, Minecraft minecraft,
            Player player) {
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

        // Phase 7.8 drops mass branch
        if (mode == WrenchItem.Mode.MASS_PLACEMENT) {
            NetworkHandler.sendToServer(new MassSelectConnectedPayload(hand.ordinal(), blockHitResult.getBlockPos()));
        } else {
            NetworkHandler.sendToServer(new CopyPasteConnectedPayload(hand.ordinal(), blockHitResult.getBlockPos()));
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private static boolean isLogisticsInteraction(Minecraft minecraft) {
        Player player = minecraft.player;
        if (isLogisticsItem(player.getMainHandItem()) || isLogisticsItem(player.getOffhandItem())) {
            return true;
        }
        return minecraft.hitResult instanceof BlockHitResult blockHitResult
                && blockHitResult.getType() == HitResult.Type.BLOCK
                && minecraft.level.getBlockState(blockHitResult.getBlockPos()).is(Registration.COMPUTER_BLOCK.get());
    }

    private static boolean isLogisticsItem(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem
                || stack.getItem() instanceof LogisticsNodeItem
                || stack.getItem() instanceof PatternSetterItem
                || stack.is(ModTags.FILTERS);
    }

    private static void resetInputState() {
        lastSentModifierMask = -1;
        forwardedUse = false;
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
