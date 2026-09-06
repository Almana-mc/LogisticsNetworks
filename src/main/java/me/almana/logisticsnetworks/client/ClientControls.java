package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ClientControls {
    public static final KeyMapping MODIFIER_1 = key(
            "key.logisticsnetworks.modifier_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping MODIFIER_2 = key(
            "key.logisticsnetworks.modifier_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping MODIFIER_3 = key(
            "key.logisticsnetworks.modifier_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping PRIMARY_INTERACTION = key(
            "key.logisticsnetworks.primary_interaction", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final KeyMapping SECONDARY_INTERACTION = key(
            "key.logisticsnetworks.secondary_interaction", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT);

    private static boolean modifier1Down;
    private static boolean modifier2Down;
    private static boolean modifier3Down;

    private ClientControls() {
    }

    private static KeyMapping key(String name, InputConstants.Type type, int code) {
        return new KeyMapping(name, KeyConflictContext.UNIVERSAL, type, code,
                WrenchHudOverlay.LOGISTICS_CATEGORY);
    }

    public static int resolveMouseAction(MouseButtonEvent event) {
        if (PRIMARY_INTERACTION.matchesMouse(event) && PRIMARY_INTERACTION.isConflictContextAndModifierActive()) return 0;
        if (SECONDARY_INTERACTION.matchesMouse(event) && SECONDARY_INTERACTION.isConflictContextAndModifierActive()) return 1;
        return -1;
    }

    public static int resolveKeyAction(KeyEvent event) {
        if (PRIMARY_INTERACTION.matches(event) && PRIMARY_INTERACTION.isConflictContextAndModifierActive()) return 0;
        if (SECONDARY_INTERACTION.matches(event) && SECONDARY_INTERACTION.isConflictContextAndModifierActive()) return 1;
        return -1;
    }

    public static int resolveMouseAction(double mouseX, double mouseY, int button) {
        return resolveMouseAction(ClientInput.mouse(mouseX, mouseY, button));
    }

    public static int resolveKeyAction(int keyCode, int scanCode, int modifiers) {
        return resolveKeyAction(ClientInput.key(keyCode, scanCode, modifiers));
    }

    public static boolean modifier1Down() {
        return modifierDown(MODIFIER_1, modifier1Down);
    }

    public static boolean modifier2Down() {
        return modifierDown(MODIFIER_2, modifier2Down);
    }

    public static boolean modifier3Down() {
        return modifierDown(MODIFIER_3, modifier3Down);
    }

    public static int modifierMask() {
        int mask = 0;
        if (modifier1Down()) mask |= 1;
        if (modifier2Down()) mask |= 2;
        if (modifier3Down()) mask |= 4;
        return mask;
    }

    public static boolean usesVanillaUseInput(Options options) {
        return SECONDARY_INTERACTION.getKey().equals(options.keyUse.getKey())
                && SECONDARY_INTERACTION.getKeyModifier() == options.keyUse.getKeyModifier();
    }

    public static double cursorX(Minecraft minecraft) {
        return minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth()
                / minecraft.getWindow().getScreenWidth();
    }

    public static double cursorY(Minecraft minecraft) {
        return minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight()
                / minecraft.getWindow().getScreenHeight();
    }

    private static boolean modifierDown(KeyMapping mapping, boolean baseKeyDown) {
        resetInactiveModifiers();
        return baseKeyDown && mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
    }

    private static void updateModifierState(InputConstants.Key key, boolean down) {
        if (MODIFIER_1.getKey().equals(key)) modifier1Down = down;
        if (MODIFIER_2.getKey().equals(key)) modifier2Down = down;
        if (MODIFIER_3.getKey().equals(key)) modifier3Down = down;
    }

    private static void resetInactiveModifiers() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.isWindowActive()) {
            modifier1Down = false;
            modifier2Down = false;
            modifier3Down = false;
        }
    }

    @EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT)
    public static class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre event) {
            resetInactiveModifiers();
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            updateModifierState(InputConstants.getKey(ClientInput.key(event.getKey(), event.getScanCode(), 0)),
                    event.getAction() != GLFW.GLFW_RELEASE);
        }

        @SubscribeEvent
        public static void onMouseInput(InputEvent.MouseButton.Pre event) {
            updateModifierState(InputConstants.Type.MOUSE.getOrCreate(event.getButton()),
                    event.getAction() != GLFW.GLFW_RELEASE);
        }

        @SubscribeEvent
        public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
            updateModifierState(InputConstants.getKey(
                    ClientInput.key(event.getKeyCode(), event.getScanCode(), 0)), true);
        }

        @SubscribeEvent
        public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
            updateModifierState(InputConstants.getKey(
                    ClientInput.key(event.getKeyCode(), event.getScanCode(), 0)), false);
        }

        @SubscribeEvent
        public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
            updateModifierState(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), true);
        }

        @SubscribeEvent
        public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
            updateModifierState(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), false);
        }
    }
}
