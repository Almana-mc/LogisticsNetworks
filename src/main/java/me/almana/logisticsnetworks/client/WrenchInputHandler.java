package me.almana.logisticsnetworks.client;

import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.client.screen.WrenchModeScreen;
import me.almana.logisticsnetworks.client.screen.WrenchSettingsScreen;
import me.almana.logisticsnetworks.integration.curios.CuriosCompat;
import me.almana.logisticsnetworks.item.LogisticsNodeItem;
import me.almana.logisticsnetworks.item.PatternSetterItem;
import me.almana.logisticsnetworks.item.WornWrench;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.CopyPasteConnectedPayload;
import me.almana.logisticsnetworks.network.CycleWrenchModePayload;
import me.almana.logisticsnetworks.network.NetworkHandler;
import me.almana.logisticsnetworks.network.SyncModifierKeysPayload;
import me.almana.logisticsnetworks.network.ToggleWornWrenchPayload;
import me.almana.logisticsnetworks.network.UseWornWrenchPayload;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import me.almana.logisticsnetworks.client.screen.NodeGraphScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
public class WrenchInputHandler {
    private static int lastSentModifierMask = -1;
    private static boolean forwardedUse;
    private static boolean useHeldLastTick;
    private static boolean useDownLastTick;
    private static boolean airUseHeld;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        useHeldLastTick = useDownLastTick;
        useDownLastTick = minecraft.options.keyUse.isDown();
        airUseHeld &= useHeldLastTick;
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

        while (ClientControls.TOGGLE_WORN_WRENCH.consumeClick()) {
            if (CuriosCompat.isLoaded()) {
                NetworkHandler.sendToServer(ToggleWornWrenchPayload.INSTANCE);
            }
        }

        while (ClientControls.WRENCH_MODES.consumeClick()) {
            InteractionHand hand = findWrenchHand(minecraft.player);
            if (hand != null && ClientControls.wrenchModesDown()) {
                minecraft.setScreen(new WrenchModeScreen(hand,
                        WrenchItem.getMode(WornWrench.orDrawn(minecraft.player, minecraft.player.getItemInHand(hand)))));
                return;
            }
        }

        while (ClientControls.SECONDARY_INTERACTION.consumeClick()) {
            if (ClientControls.usesVanillaUseInput(minecraft.options)) {
                continue;
            }
            if (!isLogisticsInteraction(minecraft) && WornWrench.drawn(minecraft.player).isEmpty()) {
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

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrawnWrenchAirUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!event.isUseItem() || player == null || player.isSpectator() || minecraft.hitResult == null
                || minecraft.hitResult.getType() != HitResult.Type.MISS
                || !ClientControls.usesVanillaUseInput(minecraft.options) && !forwardedUse) {
            return;
        }
        ItemStack wrench = WornWrench.drawn(player);
        if (wrench.isEmpty() || WrenchItem.getMode(wrench) == WrenchItem.Mode.WRENCH
                || useHeldLastTick && !airUseHeld) {
            return;
        }
        if (!airUseHeld) {
            airUseHeld = true;
            NetworkHandler.sendToServer(UseWornWrenchPayload.INSTANCE);
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrawnWrenchUse(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!event.getLevel().isClientSide() || player.isSpectator()) {
            return;
        }
        ItemStack wrench = WornWrench.drawn(player);
        if (!wrench.isEmpty()
                && WrenchItem.actsOn(event.getLevel(), wrench, event.getPos(), ClientControls.modifier1Down())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetInputState();
        NodeGraphScreen.clearSession();
    }

    // Forge wrapper names must differ
    @SubscribeEvent
    public static void onWrenchSettingsKey(InputEvent.Key event) {
        if (!ClientControls.EDIT_WRENCH_COLORS.consumeClick()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }

        InteractionHand hand = findWrenchHand(player);
        if (hand != null) {
            minecraft.setScreen(new WrenchSettingsScreen(WornWrench.orDrawn(player, player.getItemInHand(hand)), hand));
        }
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
        ItemStack wrench = WornWrench.orDrawn(player, player.getItemInHand(hand));
        if (!(wrench.getItem() instanceof WrenchItem)) {
            return;
        }

        WrenchItem.Mode mode = WrenchItem.getMode(wrench);
        if (mode != WrenchItem.Mode.COPY_PASTE) {
            return;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult)
                || blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        NetworkHandler.sendToServer(new CopyPasteConnectedPayload(hand.ordinal(), blockHitResult.getBlockPos()));
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
    public static InteractionHand findWrenchHand(Player player) {
        if (player.getMainHandItem().getItem() instanceof WrenchItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof WrenchItem) {
            return InteractionHand.OFF_HAND;
        }
        return WornWrench.drawn(player).isEmpty() ? null : InteractionHand.MAIN_HAND;
    }
}
