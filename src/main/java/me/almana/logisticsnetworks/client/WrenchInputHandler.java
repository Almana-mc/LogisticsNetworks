package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.screen.WrenchColorScreen;
import me.almana.logisticsnetworks.item.LogisticsNodeItem;
import me.almana.logisticsnetworks.item.PatternSetterItem;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.mixin.client.MinecraftInvoker;
import me.almana.logisticsnetworks.network.CopyPasteConnectedPayload;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import me.almana.logisticsnetworks.network.SyncModifierKeysPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
public class WrenchInputHandler {
    public static final KeyMapping OPEN_COLOR_EDITOR = new KeyMapping(
            "key.logisticsnetworks.wrench_colors",
            InputConstants.KEY_G,
            WrenchHudOverlay.LOGISTICS_CATEGORY);

    private static int lastSentModifierMask = -1;
    private static boolean forwardedUse;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetInputState();
            return;
        }

        int modifierMask = ClientControls.modifierMask();
        if (modifierMask != lastSentModifierMask) {
            ClientPacketDistributor.sendToServer(new SyncModifierKeysPayload(modifierMask));
            lastSentModifierMask = modifierMask;
        }

        if (minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }

        while (ClientControls.SECONDARY_INTERACTION.consumeClick()) {
            if (ClientControls.usesVanillaUseInput(minecraft.options)) {
                continue;
            }
            if (!isLogisticsInteraction(minecraft)) {
                continue;
            }
            forwardedUse = true;
            try {
                ((MinecraftInvoker) minecraft).logisticsnetworks$startUseItem();
            } finally {
                forwardedUse = false;
            }
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetInputState();
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
        if (player == null || minecraft.level == null || !ClientControls.modifier1Down()) {
            return;
        }

        if (event.getScrollDeltaY() == 0.0D) {
            return;
        }

        InteractionHand hand = findWrenchHand(player);
        if (hand == null) {
            return;
        }

        ClientPacketDistributor.sendToServer(
                new CycleWrenchModePayload(hand.ordinal(), event.getScrollDeltaY() > 0.0D));
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
        if (shouldCancelUse(isLogisticsInteraction(minecraft), sameInput, forwardedUse)) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        if (shouldTryConnectedPaste(ClientControls.modifier2Down(), sameInput, forwardedUse)) {
            tryConnectedPaste(event, minecraft, player);
        }
    }

    static boolean shouldCancelUse(boolean relevant, boolean sameInput, boolean forwarded) {
        return relevant && !sameInput && !forwarded;
    }

    static boolean shouldTryConnectedPaste(boolean modifier2Down, boolean sameInput, boolean forwarded) {
        return modifier2Down && (sameInput || forwarded);
    }

    private static boolean tryConnectedPaste(InputEvent.InteractionKeyMappingTriggered event, Minecraft minecraft,
            Player player) {
        InteractionHand hand = event.getHand();
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof WrenchItem) || WrenchItem.getMode(stack) != WrenchItem.Mode.COPY_PASTE) {
            return false;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult)
                || blockHitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        ClientPacketDistributor.sendToServer(
                new CopyPasteConnectedPayload(hand.ordinal(), blockHitResult.getBlockPos()));
        event.setSwingHand(false);
        event.setCanceled(true);
        return true;
    }

    private static boolean isLogisticsInteraction(Minecraft minecraft) {
        Player player = minecraft.player;
        if (isLogisticsItem(player.getMainHandItem()) || isLogisticsItem(player.getOffhandItem())) {
            return true;
        }
        return minecraft.hitResult instanceof BlockHitResult blockHitResult
                && blockHitResult.getType() == HitResult.Type.BLOCK
                && minecraft.level.getBlockState(blockHitResult.getBlockPos()).is(Registration.COMPUTER_BLOCK);
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
