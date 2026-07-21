package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

public class SlotNumberOverlay {

    private static boolean showSlotNumbers;

    public static final KeyMapping TOGGLE_SLOT_NUMBERS = new KeyMapping(
            "key.logisticsnetworks.toggle_slot_numbers",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_I,
            "key.categories.logisticsnetworks");

    private static void toggle() {
        showSlotNumbers = !showSlotNumbers;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            String key = showSlotNumbers
                    ? "message.logisticsnetworks.slot_numbers.enabled"
                    : "message.logisticsnetworks.slot_numbers.disabled";
            player.displayClientMessage(Component.translatable(key, TOGGLE_SLOT_NUMBERS.getTranslatedKeyMessage()), true);
        }
    }

    @EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_SLOT_NUMBERS);
        }
    }

    @EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
    public static class GameEvents {
        @SubscribeEvent
        public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
            if (TOGGLE_SLOT_NUMBERS.isActiveAndMatches(
                    InputConstants.Type.KEYSYM.getOrCreate(event.getKeyCode()))) {
                toggle();
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (TOGGLE_SLOT_NUMBERS.consumeClick()) {
                toggle();
            }
        }

        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            if (!showSlotNumbers || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
                return;
            }

            GuiGraphics graphics = event.getGuiGraphics();
            Font font = Minecraft.getInstance().font;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            for (Slot slot : screen.getMenu().slots) {
                String label = String.valueOf(slot.index);
                int textWidth = font.width(label);
                float scale = Math.min(16f / font.lineHeight, 16f / textWidth) * 0.8f;
                graphics.pose().pushPose();
                graphics.pose().translate(screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.drawString(font, label, -textWidth / 2, -font.lineHeight / 2, 0xFF7E3FB0, false);
                graphics.pose().popPose();
            }
            graphics.pose().popPose();
        }
    }
}
