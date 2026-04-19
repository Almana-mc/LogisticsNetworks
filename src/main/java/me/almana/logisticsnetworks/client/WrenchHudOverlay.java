package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.Logisticsnetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public class WrenchHudOverlay {

    private static boolean hudVisible = true;

    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.logisticsnetworks.toggle_wrench_hud",
            InputConstants.KEY_H,
            "key.categories.logisticsnetworks");

    @EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_HUD);
        }
    }

    @EventBusSubscriber(modid = Logisticsnetworks.MOD_ID, value = Dist.CLIENT)
    public static class GameEvents {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (TOGGLE_HUD.consumeClick()) {
                hudVisible = !hudVisible;
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    String key = hudVisible
                            ? "message.logisticsnetworks.wrench_hud.enabled"
                            : "message.logisticsnetworks.wrench_hud.disabled";
                    player.displayClientMessage(
                            Component.translatable(key, TOGGLE_HUD.getTranslatedKeyMessage()), true);
                }
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiLayerEvent.Post event) {
            if (!event.getName().equals(VanillaGuiLayers.HOTBAR))
                return;
            if (!hudVisible)
                return;

            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null) return;

            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            ItemStack wrenchStack = null;
            if (mainHand.getItem() instanceof WrenchItem) {
                wrenchStack = mainHand;
            } else if (offHand.getItem() instanceof WrenchItem) {
                wrenchStack = offHand;
            }
            if (wrenchStack == null) return;

            WrenchItem.Mode mode = WrenchItem.getMode(wrenchStack);
            Component modeText = Component.translatable("tooltip.logisticsnetworks.wrench.mode",
                    WrenchItem.getModeDisplayName(mode));
            Component text = WrenchItem.hasAE2Link(wrenchStack)
                    ? Component.empty().append(modeText).append(" ").append(
                            Component.literal("[ME]").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE))
                    : modeText;

            GuiGraphics g = event.getGuiGraphics();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int x = screenWidth / 2 + 98;
            int y = screenHeight - 4;

            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(mc.font, text, 0, -mc.font.lineHeight, 0xFFFFFF, true);
            g.pose().popPose();
        }
    }
}
