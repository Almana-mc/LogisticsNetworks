package me.almana.logisticsnetworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.almana.logisticsnetworks.LogisticsNetworks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class ClientControls {
    public static final String CATEGORY = "key.categories.logisticsnetworks";

    public static final KeyMapping MODIFIER_1 = key(
            "key.logisticsnetworks.modifier_1", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping MODIFIER_2 = key(
            "key.logisticsnetworks.modifier_2", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping MODIFIER_3 = key(
            "key.logisticsnetworks.modifier_3", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping PRIMARY_INTERACTION = key(
            "key.logisticsnetworks.primary_interaction", InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final KeyMapping SECONDARY_INTERACTION = key(
            "key.logisticsnetworks.secondary_interaction", InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    public static final KeyMapping TOGGLE_WRENCH_HUD = new KeyMapping(
            "key.logisticsnetworks.toggle_wrench_hud", InputConstants.KEY_H, CATEGORY);
    public static final KeyMapping TOGGLE_SLOT_NUMBERS = new KeyMapping(
            "key.logisticsnetworks.toggle_slot_numbers", KeyConflictContext.UNIVERSAL,
            KeyModifier.ALT, InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_I), CATEGORY);

    private static boolean modifier1Down;
    private static boolean modifier2Down;
    private static boolean modifier3Down;

    private ClientControls() {
    }

    private static KeyMapping key(String name, InputConstants.Type type, int code) {
        return new KeyMapping(name, KeyConflictContext.UNIVERSAL, type, code, CATEGORY);
    }

    public static int resolveMouseAction(int button) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        if (PRIMARY_INTERACTION.isActiveAndMatches(key)) return 0;
        if (SECONDARY_INTERACTION.isActiveAndMatches(key)) return 1;
        return -1;
    }

    public static int resolveKeyAction(int keyCode, int scanCode) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (PRIMARY_INTERACTION.isActiveAndMatches(key)) return 0;
        if (SECONDARY_INTERACTION.isActiveAndMatches(key)) return 1;
        return -1;
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

    @EventBusSubscriber(modid = LogisticsNetworks.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(MODIFIER_1);
            event.register(MODIFIER_2);
            event.register(MODIFIER_3);
            event.register(PRIMARY_INTERACTION);
            event.register(SECONDARY_INTERACTION);
            event.register(TOGGLE_WRENCH_HUD);
            event.register(TOGGLE_SLOT_NUMBERS);
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
            updateModifierState(InputConstants.getKey(event.getKey(), event.getScanCode()),
                    event.getAction() != GLFW.GLFW_RELEASE);
        }

        @SubscribeEvent
        public static void onMouseInput(InputEvent.MouseButton.Pre event) {
            updateModifierState(InputConstants.Type.MOUSE.getOrCreate(event.getButton()),
                    event.getAction() != GLFW.GLFW_RELEASE);
        }

        @SubscribeEvent
        public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
            updateModifierState(InputConstants.getKey(event.getKeyCode(), event.getScanCode()), true);
        }

        @SubscribeEvent
        public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
            updateModifierState(InputConstants.getKey(event.getKeyCode(), event.getScanCode()), false);
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
