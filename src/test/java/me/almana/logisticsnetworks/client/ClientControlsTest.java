package me.almana.logisticsnetworks.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientControlsTest {
    @AfterEach
    void restoreBindings() {
        ClientControls.PRIMARY_INTERACTION.setKey(ClientControls.PRIMARY_INTERACTION.getDefaultKey());
        ClientControls.SECONDARY_INTERACTION.setKey(ClientControls.SECONDARY_INTERACTION.getDefaultKey());
        KeyMapping.resetMapping();
    }

    @Test
    void resolvesKeyboardAndMouseRemaps() {
        ClientControls.PRIMARY_INTERACTION.setKey(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_K));
        ClientControls.SECONDARY_INTERACTION.setKey(InputConstants.Type.MOUSE.getOrCreate(4));

        assertEquals(0, ClientControls.resolveKeyAction(new KeyEvent(InputConstants.KEY_K, 0, 0)));
        assertEquals(1, ClientControls.resolveMouseAction(new MouseButtonEvent(0, 0, new MouseButtonInfo(4, 0))));
        assertEquals(-1, ClientControls.resolveKeyAction(new KeyEvent(InputConstants.KEY_J, 0, 0)));
        assertEquals(-1, ClientControls.resolveMouseAction(new MouseButtonEvent(0, 0, new MouseButtonInfo(3, 0))));
    }

    @Test
    void allControlsShareExistingCategory() {
        assertEquals(WrenchHudOverlay.LOGISTICS_CATEGORY, ClientControls.MODIFIER_1.getCategory());
        assertEquals(WrenchHudOverlay.LOGISTICS_CATEGORY, ClientControls.MODIFIER_2.getCategory());
        assertEquals(WrenchHudOverlay.LOGISTICS_CATEGORY, ClientControls.MODIFIER_3.getCategory());
        assertEquals(WrenchHudOverlay.LOGISTICS_CATEGORY, ClientControls.PRIMARY_INTERACTION.getCategory());
        assertEquals(WrenchHudOverlay.LOGISTICS_CATEGORY, ClientControls.SECONDARY_INTERACTION.getCategory());
        assertEquals(WrenchHudOverlay.LOGISTICS_CATEGORY, WrenchHudOverlay.TOGGLE_HUD.getCategory());
        assertEquals(WrenchHudOverlay.LOGISTICS_CATEGORY, SlotNumberOverlay.TOGGLE_SLOT_NUMBERS.getCategory());
    }

    @Test
    void controlsUseSourceDefaults() {
        assertEquals(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSHIFT),
                ClientControls.MODIFIER_1.getDefaultKey());
        assertEquals(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LCONTROL),
                ClientControls.MODIFIER_2.getDefaultKey());
        assertEquals(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LALT),
                ClientControls.MODIFIER_3.getDefaultKey());
        assertEquals(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT),
                ClientControls.PRIMARY_INTERACTION.getDefaultKey());
        assertEquals(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT),
                ClientControls.SECONDARY_INTERACTION.getDefaultKey());
        assertEquals(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_H),
                WrenchHudOverlay.TOGGLE_HUD.getDefaultKey());
        assertEquals(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_I),
                SlotNumberOverlay.TOGGLE_SLOT_NUMBERS.getDefaultKey());
        assertEquals(KeyModifier.ALT, SlotNumberOverlay.TOGGLE_SLOT_NUMBERS.getDefaultKeyModifier());
    }
}
