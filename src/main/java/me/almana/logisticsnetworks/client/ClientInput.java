package me.almana.logisticsnetworks.client;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class ClientInput {
    private ClientInput() {
    }

    public static KeyEvent key(int keyCode, int scanCode, int modifiers) {
        return new KeyEvent(keyCode, scanCode, modifiers);
    }

    public static CharacterEvent character(char codePoint) {
        return new CharacterEvent(codePoint);
    }

    public static MouseButtonEvent mouse(double mouseX, double mouseY, int button) {
        return mouse(mouseX, mouseY, button, 0);
    }

    public static MouseButtonEvent mouse(double mouseX, double mouseY, int button, int modifiers) {
        return new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, modifiers));
    }
}
