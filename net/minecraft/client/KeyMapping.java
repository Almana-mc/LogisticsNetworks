package net.minecraft.client;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class KeyMapping implements Comparable<KeyMapping>, net.neoforged.neoforge.client.extensions.IKeyMappingExtension {
    private static final Map<String, KeyMapping> ALL = Maps.newConcurrentMap();
    private static final net.neoforged.neoforge.client.settings.KeyMappingLookup MAP = new net.neoforged.neoforge.client.settings.KeyMappingLookup();
    private final String name;
    private final InputConstants.Key defaultKey;
    private final KeyMapping.Category category;
    protected InputConstants.Key key;
    boolean isDown;
    private int clickCount;
    private final int order;
    // Neo: Injected Key Mapping controls
    private net.neoforged.neoforge.client.settings.KeyModifier keyModifierDefault = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
    private net.neoforged.neoforge.client.settings.KeyModifier keyModifier = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
    private net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext = net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL;

    public static void click(InputConstants.Key key) {
        forAllKeyMappings(key, keyMapping -> keyMapping.clickCount++);
    }

    public static void set(InputConstants.Key key, boolean state) {
        forAllKeyMappings(key, keyMapping -> keyMapping.setDown(state), !state);
    }

    private static void forAllKeyMappings(InputConstants.Key key, Consumer<KeyMapping> operation) {
        forAllKeyMappings(key, operation, false);
    }

    private static void forAllKeyMappings(InputConstants.Key key, Consumer<KeyMapping> operation, boolean releasing) {
        List<KeyMapping> keyMappings = MAP.getAll(key, releasing);
        if (keyMappings != null && !keyMappings.isEmpty()) {
            for (KeyMapping keyMapping : keyMappings) {
                operation.accept(keyMapping);
            }
        }
    }

    public static void setAll() {
        Window window = Minecraft.getInstance().getWindow();

        for (KeyMapping keyMapping : ALL.values()) {
            if (keyMapping.shouldSetOnIngameFocus()) {
                keyMapping.setDown(InputConstants.isKeyDown(window, keyMapping.key.getValue()));
            }
        }
    }

    public static void releaseAll() {
        for (KeyMapping keyMapping : ALL.values()) {
            keyMapping.release();
        }
    }

    public static void restoreToggleStatesOnScreenClosed() {
        for (KeyMapping keyMapping : ALL.values()) {
            if (keyMapping instanceof ToggleKeyMapping toggleKeyMapping && toggleKeyMapping.shouldRestoreStateOnScreenClosed()) {
                toggleKeyMapping.setDown(true);
            }
        }
    }

    public static void resetToggleKeys() {
        for (KeyMapping keyMapping : ALL.values()) {
            if (keyMapping instanceof ToggleKeyMapping toggleKeyMapping) {
                toggleKeyMapping.reset();
            }
        }
    }

    public static void resetMapping() {
        MAP.clear();

        for (KeyMapping keyMapping : ALL.values()) {
            keyMapping.registerMapping(keyMapping.key);
        }
    }

    public KeyMapping(String name, int keysym, KeyMapping.Category category) {
        this(name, InputConstants.Type.KEYSYM, keysym, category);
    }

    public KeyMapping(String name, InputConstants.Type type, int value, KeyMapping.Category category) {
        this(name, type, value, category, 0);
    }

    public KeyMapping(String name, InputConstants.Type type, int value, KeyMapping.Category category, int order) {
        this.name = name;
        this.key = type.getOrCreate(value);
        this.defaultKey = this.key;
        this.category = category;
        this.order = order;
        ALL.put(name, this);
        this.registerMapping(this.key);
    }

    // Neo: Injected Key Mapping constructors to assist modders
    /**
     * Convenience constructor for creating KeyMappings with keyConflictContext set.
     */
    public KeyMapping(String name, net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext, InputConstants.Type inputType, int keyCode, KeyMapping.Category category) {
        this(name, keyConflictContext, inputType.getOrCreate(keyCode), category);
    }

    /**
     * Convenience constructor for creating KeyMappings with keyConflictContext set.
     */
    public KeyMapping(String name, net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext, InputConstants.Key keyCode, KeyMapping.Category category) {
        this(name, keyConflictContext, net.neoforged.neoforge.client.settings.KeyModifier.NONE, keyCode, category);
    }

    /**
     * Convenience constructor for creating KeyMappings with keyConflictContext and keyModifier set.
     */
    public KeyMapping(String name, net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext, net.neoforged.neoforge.client.settings.KeyModifier keyModifier, InputConstants.Type inputType, int keyCode, KeyMapping.Category category) {
        this(name, keyConflictContext, keyModifier, inputType.getOrCreate(keyCode), category);
    }

    /**
     * Convenience constructor for creating KeyMappings with keyConflictContext and keyModifier set.
     */
    public KeyMapping(String name, net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext, net.neoforged.neoforge.client.settings.KeyModifier keyModifier, InputConstants.Key keyCode, KeyMapping.Category category) {
        this.name = name;
        this.key = keyCode;
        this.defaultKey = keyCode;
        this.category = category;
        this.order = 0; // TODO 1.21.11: should we add additional constructor overloads to specify this?
        this.keyConflictContext = keyConflictContext;
        this.keyModifier = keyModifier;
        this.keyModifierDefault = keyModifier;
        if (this.keyModifier.matches(keyCode))
            this.keyModifier = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
        ALL.put(name, this);
        MAP.put(keyCode, this);
    }

    @Override
    public InputConstants.Key getKey() {
        return key;
    }

    @Override
    public void setKeyConflictContext(net.neoforged.neoforge.client.settings.IKeyConflictContext keyConflictContext) {
        this.keyConflictContext = keyConflictContext;
    }

    @Override
    public net.neoforged.neoforge.client.settings.IKeyConflictContext getKeyConflictContext() {
        return keyConflictContext;
    }

    @Override
    public net.neoforged.neoforge.client.settings.KeyModifier getDefaultKeyModifier() {
        return keyModifierDefault;
    }

    @Override
    public net.neoforged.neoforge.client.settings.KeyModifier getKeyModifier() {
        return keyModifier;
    }

    @Override
    public void setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier keyModifier, InputConstants.Key keyCode) {
        this.key = keyCode;
        if (keyModifier.matches(keyCode))
            keyModifier = net.neoforged.neoforge.client.settings.KeyModifier.NONE;
        MAP.remove(this);
        this.keyModifier = keyModifier;
        MAP.put(keyCode, this);
    }

    public boolean isDown() {
        return this.isDown && isConflictContextAndModifierActive();
    }

    public KeyMapping.Category getCategory() {
        return this.category;
    }

    public boolean consumeClick() {
        if (this.clickCount == 0) {
            return false;
        } else {
            this.clickCount--;
            return true;
        }
    }

    protected void release() {
        this.clickCount = 0;
        this.setDown(false);
    }

    protected boolean shouldSetOnIngameFocus() {
        return this.key.getType() == InputConstants.Type.KEYSYM && this.key.getValue() != InputConstants.UNKNOWN.getValue();
    }

    public String getName() {
        return this.name;
    }

    public InputConstants.Key getDefaultKey() {
        return this.defaultKey;
    }

    public void setKey(InputConstants.Key key) {
        this.key = key;
    }

    public int compareTo(KeyMapping o) {
        if (this.category == o.category) {
            return this.order == o.order ? I18n.get(this.name).compareTo(I18n.get(o.name)) : Integer.compare(this.order, o.order);
        } else {
            return Integer.compare(KeyMapping.Category.SORT_ORDER.indexOf(this.category), KeyMapping.Category.SORT_ORDER.indexOf(o.category));
        }
    }

    public static Supplier<Component> createNameSupplier(String key) {
        KeyMapping map = ALL.get(key);
        return map == null ? () -> Component.translatable(key) : map::getTranslatedKeyMessage;
    }

    public boolean same(KeyMapping that) {
        if (getKeyConflictContext().conflicts(that.getKeyConflictContext()) || that.getKeyConflictContext().conflicts(getKeyConflictContext())) {
            net.neoforged.neoforge.client.settings.KeyModifier keyModifier = getKeyModifier();
            net.neoforged.neoforge.client.settings.KeyModifier otherKeyModifier = that.getKeyModifier();
            if (keyModifier.matches(that.getKey()) || otherKeyModifier.matches(getKey())) {
                return true;
            } else if (getKey().equals(that.getKey())) {
                // IN_GAME key contexts have a conflict when at least one modifier is NONE.
                // For example: If you hold shift to crouch, you can still press E to open your inventory. This means that a Shift+E hotkey is in conflict with E.
                // GUI and other key contexts do not have this limitation.
                return keyModifier == otherKeyModifier ||
                    (getKeyConflictContext().conflicts(net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME) &&
                    (keyModifier == net.neoforged.neoforge.client.settings.KeyModifier.NONE || otherKeyModifier == net.neoforged.neoforge.client.settings.KeyModifier.NONE));
            }
        }
        return this.key.equals(that.key);
    }

    public boolean isUnbound() {
        return this.key.equals(InputConstants.UNKNOWN);
    }

    public boolean matches(KeyEvent event) {
        return event.key() == InputConstants.UNKNOWN.getValue()
            ? this.key.getType() == InputConstants.Type.SCANCODE && this.key.getValue() == event.scancode()
            : this.key.getType() == InputConstants.Type.KEYSYM && this.key.getValue() == event.key();
    }

    public boolean matchesMouse(MouseButtonEvent event) {
        return this.key.getType() == InputConstants.Type.MOUSE && this.key.getValue() == event.button();
    }

    public Component getTranslatedKeyMessage() {
        return getKeyModifier().getCombinedName(key, () -> {
        return this.key.getDisplayName();
        });
    }

    public boolean isDefault() {
        return this.key.equals(this.defaultKey) && getKeyModifier() == getDefaultKeyModifier();
    }

    public String saveString() {
        return this.key.getName();
    }

    public void setDown(boolean down) {
        this.isDown = down;
    }

    private void registerMapping(InputConstants.Key key) {
        MAP.put(key, this);
    }

    public static @Nullable KeyMapping get(String name) {
        return ALL.get(name);
    }

    @OnlyIn(Dist.CLIENT)
    public record Category(Identifier id) {
        static final List<KeyMapping.Category> SORT_ORDER = new ArrayList<>();
        public static final KeyMapping.Category MOVEMENT = register("movement");
        public static final KeyMapping.Category MISC = register("misc");
        public static final KeyMapping.Category MULTIPLAYER = register("multiplayer");
        public static final KeyMapping.Category GAMEPLAY = register("gameplay");
        public static final KeyMapping.Category INVENTORY = register("inventory");
        public static final KeyMapping.Category CREATIVE = register("creative");
        public static final KeyMapping.Category SPECTATOR = register("spectator");
        public static final KeyMapping.Category DEBUG = register("debug");

        private static KeyMapping.Category register(String name) {
            return register(Identifier.withDefaultNamespace(name));
        }

        /**
         * @deprecated Neo: use {@link net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent#registerCategory(Category)} instead
         */
        @Deprecated
        public static KeyMapping.Category register(Identifier id) {
            KeyMapping.Category category = new KeyMapping.Category(id);
            if (SORT_ORDER.contains(category)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "Category '%s' is already registered.", id));
            } else {
                SORT_ORDER.add(category);
                return category;
            }
        }

        public Component label() {
            return Component.translatable(this.id.toLanguageKey("key.category"));
        }
    }
}
