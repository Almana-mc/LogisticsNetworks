package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.item.WrenchItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    public static final KeyMapping.Category LOGISTICS_CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(LogisticsNetworks.MOD_ID, "logisticsnetworks"));

    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.logisticsnetworks.toggle_wrench_hud",
            InputConstants.KEY_H,
            LOGISTICS_CATEGORY);

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(LOGISTICS_CATEGORY);
        event.register(TOGGLE_HUD);
    }

    @EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
    public static class GameEvents {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (TOGGLE_HUD.consumeClick()) {
                hudVisible = !hudVisible;
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    String key = hudVisible
                            ? "message.logisticsnetworks.wrench_hud.enabled"
                            : "message.logisticsnetworks.wrench_hud.disabled";
                    player.sendOverlayMessage(
                            Component.translatable(key, TOGGLE_HUD.getTranslatedKeyMessage()));
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
            LocalPlayer player = mc.player;
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
            var storageLink = WrenchItem.getStorageLink(wrenchStack);
            Component text = storageLink != null
                    ? Component.empty().append(modeText).append(" ").append(
                            Component.literal("[").append(storageLink.backend().displayName()).append("]")
                                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE))
                    : modeText;

            GuiGraphics g = new GuiGraphics(event.getGuiGraphics());
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int textWidth = mc.font.width(text);
            int x = Math.min(screenWidth / 2 + 98, screenWidth - (int) Math.ceil(textWidth * 0.75) - 8);
            int y = screenHeight - 4;

            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.fill(-5, -mc.font.lineHeight - 3, textWidth + 4, 3, 0xCC101010);
            int color = WrenchItem.getModeDisplayName(mode).getStyle().getColor().getValue();
            g.fill(-5, -mc.font.lineHeight - 3, -2, 3, 0xFF000000 | color);
            g.drawString(mc.font, text, 0, -mc.font.lineHeight, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
    }
}
