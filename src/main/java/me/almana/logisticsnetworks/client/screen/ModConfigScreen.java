package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.ClientConfig;
import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.client.theme.Themes;
import me.almana.logisticsnetworks.upgrade.UpgradeLimitsConfig;
import me.almana.logisticsnetworks.upgrade.UpgradeLimitsConfig.TierLimits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ModConfigScreen extends Screen {

    private static final int GUI_WIDTH = 360;
    private static final int GUI_HEIGHT = 230;

    private static final int COL_PAPER_TOP   = 0xFFF8EDDA;
    private static final int COL_PAPER_BTM   = 0xFFEDD9B5;
    private static final int COL_EDGE        = 0xFFCFB896;
    private static final int COL_BORDER      = 0xFFB89A6A;
    private static final int COL_SHADOW      = 0x55000000;
    private static final int COL_SHADOW_SOFT = 0x33000000;
    private static final int COL_VIGNETTE    = 0x20704020;

    private static final int COL_INK        = 0xFF0A0400;
    private static final int COL_INK_DIM    = 0xFF2A1808;
    private static final int COL_INK_FADED  = 0xFF6A5030;
    private static final int COL_INK_TITLE  = 0xFF000000;

    private static final int COL_HOVER      = 0x30C8A030;

    private static final int COL_TAB_ACTIVE   = 0xFFF5E6C8;
    private static final int COL_TAB_INACTIVE = 0xFFCFB896;

    private static final int COL_INK_LOCKED = 0xFF8A7A6A;

    private static final int COL_TIER_BG    = 0x18704020;
    private static final int COL_TIER_HOVER = 0x30C8A030;

    private static final Tab[] TABS = Tab.values();

    private static final Component TEXT_DONE = Component.translatable("gui.logisticsnetworks.config.done");
    private static final Component TEXT_CANCEL = Component.translatable("gui.logisticsnetworks.config.cancel");

    private static final Component[] TAB_LABELS = {
        Component.translatable("gui.logisticsnetworks.config.tab.common"),
        Component.translatable("gui.logisticsnetworks.config.tab.client"),
        Component.translatable("gui.logisticsnetworks.config.tab.upgrades")
    };

    private static final Component TEXT_DROP_NODE = Component.translatable("gui.logisticsnetworks.config.common.dropNodeItem");
    private static final Component TEXT_DEBUG = Component.translatable("gui.logisticsnetworks.config.common.debugMode");
    private static final Component TEXT_BACKOFF_TICKS = Component.translatable("gui.logisticsnetworks.config.common.backoffMaxTicks");
    private static final Component TEXT_BACKOFF_ITEM = Component.translatable("gui.logisticsnetworks.config.common.backoffItem");
    private static final Component TEXT_BACKOFF_FLUID = Component.translatable("gui.logisticsnetworks.config.common.backoffFluid");
    private static final Component TEXT_BACKOFF_ENERGY = Component.translatable("gui.logisticsnetworks.config.common.backoffEnergy");
    private static final Component TEXT_BACKOFF_CHEMICAL = Component.translatable("gui.logisticsnetworks.config.common.backoffChemical");
    private static final Component TEXT_BACKOFF_SOURCE = Component.translatable("gui.logisticsnetworks.config.common.backoffSource");

    private static final Component TEXT_MAX_RENDERED = Component.translatable("gui.logisticsnetworks.config.client.maxRenderedNodes");
    private static final Component TEXT_MAX_VISIBLE = Component.translatable("gui.logisticsnetworks.config.client.maxVisibleNodes");

    private static final Component[] TIER_LABELS = {
        Component.translatable("gui.logisticsnetworks.config.upgrades.tier.none"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.tier.iron"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.tier.gold"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.tier.diamond"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.tier.netherite")
    };

    private static final Component[] FIELD_LABELS = {
        Component.translatable("gui.logisticsnetworks.config.upgrades.minTicks"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.itemBatch"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.fluidBatch"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.energyBatch"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.chemicalBatch"),
        Component.translatable("gui.logisticsnetworks.config.upgrades.sourceBatch")
    };

    private static final Component TEXT_NO_PERMISSION = Component.translatable("gui.logisticsnetworks.config.no_permission");

    private enum Tab { COMMON, CLIENT, UPGRADES }

    private final Screen parent;
    private int x0, y0;
    private Tab currentTab = Tab.COMMON;

    private boolean pendingDropNodeItem;
    private boolean pendingDebugMode;
    private boolean pendingBackoffItem;
    private boolean pendingBackoffFluid;
    private boolean pendingBackoffEnergy;
    private boolean pendingBackoffChemical;
    private boolean pendingBackoffSource;
    private int pendingBackoffMaxTicks;
    private EditBox backoffMaxTicksBox;

    private int pendingMaxRenderedNodes;
    private int pendingMaxVisibleNodes;
    private EditBox maxRenderedNodesBox;
    private EditBox maxVisibleNodesBox;
    private String pendingTheme;

    private TierLimits[] pendingTiers;
    private int expandedTier = -1;
    private EditBox[] upgradeBoxes = new EditBox[6];

    private String editStartValue = "";
    private boolean canEditServerConfig;

    public ModConfigScreen(Screen parent) {
        super(Component.translatable("gui.logisticsnetworks.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        x0 = (width - GUI_WIDTH) / 2;
        y0 = (height - GUI_HEIGHT) / 2;

        canEditServerConfig = minecraft.player == null || minecraft.isLocalServer()
                || minecraft.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);

        pendingDropNodeItem = Config.dropNodeItemSpec.get();
        pendingDebugMode = Config.debugModeSpec.get();
        pendingBackoffItem = Config.backoffItemSpec.get();
        pendingBackoffFluid = Config.backoffFluidSpec.get();
        pendingBackoffEnergy = Config.backoffEnergySpec.get();
        pendingBackoffChemical = Config.backoffChemicalSpec.get();
        pendingBackoffSource = Config.backoffSourceSpec.get();
        pendingBackoffMaxTicks = Config.backoffMaxTicksSpec.get();
        pendingMaxRenderedNodes = ClientConfig.maxRenderedNodesSpec.get();
        pendingMaxVisibleNodes = ClientConfig.maxVisibleNodesSpec.get();
        pendingTheme = ClientConfig.themeSpec.get();
        pendingTiers = UpgradeLimitsConfig.getAll();

        buildTab();
    }

    private void buildTab() {
        clearWidgets();
        backoffMaxTicksBox = null;
        maxRenderedNodesBox = null;
        maxVisibleNodesBox = null;
        upgradeBoxes = new EditBox[6];

        int doneW = 60;
        int cancelW = 60;
        int gap = 8;
        int totalW = doneW + gap + cancelW;
        int btnY = y0 + GUI_HEIGHT - 24;
        int btnStartX = x0 + (GUI_WIDTH - totalW) / 2;

        addRenderableWidget(Button.builder(TEXT_DONE, b -> save())
                .bounds(btnStartX, btnY, doneW, 18).build());
        addRenderableWidget(Button.builder(TEXT_CANCEL, b -> cancel())
                .bounds(btnStartX + doneW + gap, btnY, cancelW, 18).build());

        int contentX = x0 + 10;
        int contentY = y0 + 36;
        int contentW = GUI_WIDTH - 20;

        switch (currentTab) {
            case COMMON -> buildCommonTab(contentX, contentY, contentW);
            case CLIENT -> buildClientTab(contentX, contentY, contentW);
            case UPGRADES -> buildUpgradesTab(contentX, contentY, contentW);
        }
    }

    private void buildCommonTab(int cx, int cy, int cw) {
        if (canEditServerConfig) {
            backoffMaxTicksBox = new EditBox(font, cx + 130, cy + 42, 60, 14, Component.empty());
            backoffMaxTicksBox.setMaxLength(4);
            backoffMaxTicksBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
            backoffMaxTicksBox.setValue(String.valueOf(pendingBackoffMaxTicks));
            backoffMaxTicksBox.setBordered(false);
            addWidget(backoffMaxTicksBox);
        }
    }

    private void buildClientTab(int cx, int cy, int cw) {
        maxRenderedNodesBox = new EditBox(font, cx + 150, cy + 4, 80, 14, Component.empty());
        maxRenderedNodesBox.setMaxLength(10);
        maxRenderedNodesBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        maxRenderedNodesBox.setValue(String.valueOf(pendingMaxRenderedNodes));
        maxRenderedNodesBox.setBordered(false);
        addWidget(maxRenderedNodesBox);

        maxVisibleNodesBox = new EditBox(font, cx + 150, cy + 24, 80, 14, Component.empty());
        maxVisibleNodesBox.setMaxLength(10);
        maxVisibleNodesBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        maxVisibleNodesBox.setValue(String.valueOf(pendingMaxVisibleNodes));
        maxVisibleNodesBox.setBordered(false);
        addWidget(maxVisibleNodesBox);
    }

    private void buildUpgradesTab(int cx, int cy, int cw) {
        upgradeBoxes = new EditBox[6];

        if (expandedTier >= 0 && expandedTier < 5 && canEditServerConfig) {
            TierLimits t = pendingTiers[expandedTier];
            int boxY = cy + (expandedTier + 1) * 18 + 4;
            int[] vals = { t.minTicks(), t.itemBatch(), t.fluidBatch(), t.energyBatch(), t.chemicalBatch(), t.sourceBatch() };

            int colW = (cw - 20) / 2;
            for (int i = 0; i < 6; i++) {
                int col = i < 3 ? 0 : 1;
                int row = i < 3 ? i : i - 3;
                int bx = cx + 4 + col * (colW + 12);
                int by = boxY + row * 22;

                EditBox box = new EditBox(font, bx + 80, by, 80, 14, Component.empty());
                box.setMaxLength(10);
                box.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
                box.setValue(String.valueOf(vals[i]));
                box.setBordered(false);
                addWidget(box);
                upgradeBoxes[i] = box;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(x0 + 4, y0 + 4, x0 + GUI_WIDTH + 4, y0 + GUI_HEIGHT + 4, COL_SHADOW);
        g.fill(x0 + 3, y0 + 3, x0 + GUI_WIDTH + 3, y0 + GUI_HEIGHT + 3, COL_SHADOW_SOFT);

        g.fill(x0, y0, x0 + GUI_WIDTH, y0 + GUI_HEIGHT, COL_EDGE);

        g.fillGradient(x0 + 3, y0 + 3, x0 + GUI_WIDTH - 3, y0 + GUI_HEIGHT - 3, COL_PAPER_TOP, COL_PAPER_BTM);

        g.fill(x0 + 3, y0 + 3, x0 + GUI_WIDTH - 3, y0 + 5, COL_VIGNETTE);
        g.fill(x0 + 3, y0 + GUI_HEIGHT - 5, x0 + GUI_WIDTH - 3, y0 + GUI_HEIGHT - 3, COL_VIGNETTE);
        g.fill(x0 + 3, y0 + 5, x0 + 5, y0 + GUI_HEIGHT - 5, COL_VIGNETTE);
        g.fill(x0 + GUI_WIDTH - 5, y0 + 5, x0 + GUI_WIDTH - 3, y0 + GUI_HEIGHT - 5, COL_VIGNETTE);

        g.outline(x0, y0, GUI_WIDTH, GUI_HEIGHT, COL_BORDER);

        int titleW = font.width(title);
        g.text(font, title, x0 + (GUI_WIDTH - titleW) / 2, y0 + 5, COL_INK_TITLE, false);

        g.fill(x0 + 8, y0 + 16, x0 + GUI_WIDTH - 8, y0 + 17, COL_BORDER);

        renderTabs(g, mouseX, mouseY);

        int contentX = x0 + 10;
        int contentY = y0 + 36;
        int contentW = GUI_WIDTH - 20;

        switch (currentTab) {
            case COMMON -> renderCommonTab(g, contentX, contentY, contentW, mouseX, mouseY);
            case CLIENT -> renderClientTab(g, contentX, contentY, contentW, mouseX, mouseY);
            case UPGRADES -> renderUpgradesTab(g, contentX, contentY, contentW, mouseX, mouseY);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        renderEditBox(g, backoffMaxTicksBox);
        renderEditBox(g, maxRenderedNodesBox);
        renderEditBox(g, maxVisibleNodesBox);
        for (EditBox box : upgradeBoxes) {
            renderEditBox(g, box);
        }

        if (!canEditServerConfig && (currentTab == Tab.COMMON || currentTab == Tab.UPGRADES)) {
            int tipH = GUI_HEIGHT - 60;
            if (mouseX >= contentX && mouseX < contentX + contentW && mouseY >= contentY && mouseY < contentY + tipH) {
                g.setComponentTooltipForNextFrame(font, List.of(TEXT_NO_PERMISSION), mouseX, mouseY);
            }
        }
    }

    private void renderEditBox(GuiGraphicsExtractor g, EditBox box) {
        if (box == null) return;
        String value = box.getValue();
        int textX = box.getX();
        int textY = box.getY() + (box.getHeight() - 8) / 2;

        g.text(font, value, textX, textY, COL_INK, false);

        if (box.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorPos = Math.min(box.getCursorPosition(), value.length());
            int cursorX = textX + font.width(value.substring(0, cursorPos));
            g.fill(cursorX, textY - 1, cursorX + 1, textY + 9, COL_INK);
        }
    }

    private void renderTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tabW = 80;
        int tabH = 14;
        int totalTabW = TABS.length * tabW + (TABS.length - 1) * 4;
        int tabStartX = x0 + (GUI_WIDTH - totalTabW) / 2;
        int tabY = y0 + 18;

        for (int i = 0; i < TABS.length; i++) {
            int tx = tabStartX + i * (tabW + 4);
            boolean active = TABS[i] == currentTab;
            boolean hovered = mouseX >= tx && mouseX < tx + tabW && mouseY >= tabY && mouseY < tabY + tabH;

            int bg = active ? COL_TAB_ACTIVE : (hovered ? COL_HOVER : COL_TAB_INACTIVE);
            g.fill(tx, tabY, tx + tabW, tabY + tabH, bg);

            if (active) {
                g.fill(tx, tabY, tx + tabW, tabY + 1, COL_BORDER);
                g.fill(tx, tabY, tx + 1, tabY + tabH, COL_BORDER);
                g.fill(tx + tabW - 1, tabY, tx + tabW, tabY + tabH, COL_BORDER);
            } else {
                g.outline(tx, tabY, tabW, tabH, COL_BORDER);
            }

            int textColor = active ? COL_INK : COL_INK_FADED;
            g.text(font, TAB_LABELS[i], tx + (tabW - font.width(TAB_LABELS[i])) / 2, tabY + 3, textColor, false);
        }
    }

    private void renderCommonTab(GuiGraphicsExtractor g, int cx, int cy, int cw, int mx, int my) {
        boolean locked = !canEditServerConfig;
        int y = cy;

        y = renderCheckbox(g, cx, y, cw, TEXT_DROP_NODE, pendingDropNodeItem, mx, my, locked);
        y = renderCheckbox(g, cx, y, cw, TEXT_DEBUG, pendingDebugMode, mx, my, locked);

        int labelColor = locked ? COL_INK_LOCKED : COL_INK;
        g.text(font, TEXT_BACKOFF_TICKS, cx, cy + 46, labelColor, false);
        if (locked) {
            g.text(font, String.valueOf(pendingBackoffMaxTicks), cx + 130, cy + 45, COL_INK_LOCKED, false);
        } else {
            renderUnderline(g, cx + 130, cy + 42 + 14, 60);
        }

        y = cy + 62;
        y = renderCheckbox(g, cx, y, cw, TEXT_BACKOFF_ITEM, pendingBackoffItem, mx, my, locked);
        y = renderCheckbox(g, cx, y, cw, TEXT_BACKOFF_FLUID, pendingBackoffFluid, mx, my, locked);
        y = renderCheckbox(g, cx, y, cw, TEXT_BACKOFF_ENERGY, pendingBackoffEnergy, mx, my, locked);
        y = renderCheckbox(g, cx, y, cw, TEXT_BACKOFF_CHEMICAL, pendingBackoffChemical, mx, my, locked);
        renderCheckbox(g, cx, y, cw, TEXT_BACKOFF_SOURCE, pendingBackoffSource, mx, my, locked);
    }

    private void renderClientTab(GuiGraphicsExtractor g, int cx, int cy, int cw, int mx, int my) {
        g.text(font, TEXT_MAX_RENDERED, cx, cy + 7, COL_INK, false);
        renderUnderline(g, cx + 150, cy + 4 + 14, 80);

        g.text(font, TEXT_MAX_VISIBLE, cx, cy + 27, COL_INK, false);
        renderUnderline(g, cx + 150, cy + 24 + 14, 80);

        int themeY = cy + 48;
        g.text(font, Component.translatable("gui.logisticsnetworks.config.client.theme"), cx, themeY, COL_INK, false);

        int cols = 4;
        int swatchGap = 4;
        int swatchW = (cw - (cols - 1) * swatchGap) / cols;
        int swatchH = 22;
        int startY = themeY + 12;
        GuiGraphics graphics = new GuiGraphics(g);
        Theme frame = ThemeState.active();
        for (int i = 0; i < Themes.ALL.size(); i++) {
            Theme preview = Themes.ALL.get(i);
            int col = i % cols;
            int row = i / cols;
            int sx = cx + col * (swatchW + swatchGap);
            int sy = startY + row * (swatchH + swatchGap);
            boolean active = preview.id().equals(pendingTheme);
            boolean hovered = mx >= sx && mx <= sx + swatchW && my >= sy && my <= sy + swatchH;
            ThemePaint.swatchPreview(graphics, sx, sy, swatchW, 12, preview, frame);
            int fg = active ? 0xFF000000 : (hovered ? COL_INK : COL_INK_FADED);
            ThemePaint.drawCentered(graphics, font, preview.label(), sx + swatchW / 2, sy + 13, fg);
            if (active) {
                g.outline(sx - 1, sy - 1, swatchW + 2, swatchH + 2, COL_BORDER);
            }
        }
    }

    private boolean handleClientClick(double mouseX, double mouseY, int cx, int cy, int cw) {
        int themeY = cy + 48;
        int cols = 4;
        int swatchGap = 4;
        int swatchW = (cw - (cols - 1) * swatchGap) / cols;
        int swatchH = 22;
        int startY = themeY + 12;
        for (int i = 0; i < Themes.ALL.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = cx + col * (swatchW + swatchGap);
            int sy = startY + row * (swatchH + swatchGap);
            if (mouseX >= sx && mouseX <= sx + swatchW && mouseY >= sy && mouseY <= sy + swatchH) {
                pendingTheme = Themes.ALL.get(i).id();
                return true;
            }
        }
        return false;
    }

    private void renderUpgradesTab(GuiGraphicsExtractor g, int cx, int cy, int cw, int mx, int my) {
        boolean locked = !canEditServerConfig;
        int y = cy;
        for (int tier = 0; tier < 5; tier++) {
            boolean expanded = tier == expandedTier;
            boolean hovered = mx >= cx && mx < cx + cw && my >= y && my < y + 16;

            int headerBg = hovered ? COL_TIER_HOVER : COL_TIER_BG;
            g.fill(cx, y, cx + cw, y + 16, headerBg);
            g.fill(cx, y + 16, cx + cw, y + 17, COL_BORDER);

            int tierTextColor = locked ? COL_INK_LOCKED : (expanded ? COL_INK : COL_INK_DIM);
            String arrow = expanded ? "\u25BC " : "\u25B6 ";
            g.text(font, arrow + TIER_LABELS[tier].getString(), cx + 4, y + 4, tierTextColor, false);
            y += 18;

            if (expanded) {
                TierLimits t = pendingTiers[tier];
                int[] vals = { t.minTicks(), t.itemBatch(), t.fluidBatch(), t.energyBatch(), t.chemicalBatch(), t.sourceBatch() };
                int colW = (cw - 20) / 2;
                for (int i = 0; i < 6; i++) {
                    int col = i < 3 ? 0 : 1;
                    int row = i < 3 ? i : i - 3;
                    int lx = cx + 4 + col * (colW + 12);
                    int ly = y + row * 22 + 3;
                    int fieldColor = locked ? COL_INK_LOCKED : COL_INK_DIM;
                    g.text(font, FIELD_LABELS[i], lx, ly, fieldColor, false);
                    if (locked) {
                        g.text(font, String.valueOf(vals[i]), lx + 80, ly, COL_INK_LOCKED, false);
                    } else {
                        renderUnderline(g, lx + 80, ly + 14, 80);
                    }
                }
                y += 3 * 22 + 4;
            }
        }
    }

    private int renderCheckbox(GuiGraphicsExtractor g, int cx, int y, int cw, Component label, boolean value, int mx, int my, boolean locked) {
        int boxX = cx + cw - 14;
        int boxY = y + 2;
        int boxSize = 9;

        int labelColor = locked ? COL_INK_LOCKED : COL_INK;
        int boxColor = locked ? COL_INK_LOCKED : COL_INK_DIM;
        int checkColor = locked ? COL_INK_LOCKED : COL_INK;

        g.text(font, label, cx, y + 3, labelColor, false);

        g.outline(boxX, boxY, boxSize, boxSize, boxColor);
        g.fill(boxX + 1, boxY + 1, boxX + boxSize - 1, boxY + boxSize - 1, 0xFFEDD9B5);

        if (value) {
            g.fill(boxX + 1, boxY + 5, boxX + 3, boxY + 7, checkColor);
            g.fill(boxX + 2, boxY + 6, boxX + 4, boxY + 8, checkColor);
            g.fill(boxX + 3, boxY + 4, boxX + 5, boxY + 6, checkColor);
            g.fill(boxX + 4, boxY + 3, boxX + 6, boxY + 5, checkColor);
            g.fill(boxX + 5, boxY + 2, boxX + 7, boxY + 4, checkColor);
        }

        return y + 18;
    }

    private void renderUnderline(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, COL_INK_DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        EditBox prev = findFocusedEditBox();

        if (button == 0) {
            if (handleTabClick(mouseX, mouseY)) {
                unfocusEditBoxes();
                return true;
            }

            int contentX = x0 + 10;
            int contentY = y0 + 36;
            int contentW = GUI_WIDTH - 20;

            if (canEditServerConfig) {
                switch (currentTab) {
                    case COMMON -> { if (handleCommonClick(mouseX, mouseY, contentX, contentY, contentW)) { unfocusEditBoxes(); return true; } }
                    case UPGRADES -> { if (handleUpgradesClick(mouseX, mouseY, contentX, contentY, contentW)) { unfocusEditBoxes(); return true; } }
                    case CLIENT -> { }
                }
            }

            if (currentTab == Tab.CLIENT && handleClientClick(mouseX, mouseY, contentX, contentY, contentW)) {
                unfocusEditBoxes();
                return true;
            }
        }

        boolean result = super.mouseClicked(event, doubleClick);

        EditBox now = findFocusedEditBox();
        if (now != null && now != prev) {
            editStartValue = now.getValue();
        }

        return result;
    }

    private boolean handleTabClick(double mx, double my) {
        int tabW = 80;
        int tabH = 14;
        int totalTabW = TABS.length * tabW + (TABS.length - 1) * 4;
        int tabStartX = x0 + (GUI_WIDTH - totalTabW) / 2;
        int tabY = y0 + 18;

        for (int i = 0; i < TABS.length; i++) {
            int tx = tabStartX + i * (tabW + 4);
            if (mx >= tx && mx < tx + tabW && my >= tabY && my < tabY + tabH) {
                if (TABS[i] != currentTab) {
                    stashCurrentTab();
                    currentTab = TABS[i];
                    buildTab();
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleCommonClick(double mx, double my, int cx, int cy, int cw) {
        int boxX = cx + cw - 14;
        int boxSize = 9;

        int y = cy;
        if (inBox(mx, my, boxX, y + 2, boxSize)) { pendingDropNodeItem = !pendingDropNodeItem; return true; }
        y += 18;
        if (inBox(mx, my, boxX, y + 2, boxSize)) { pendingDebugMode = !pendingDebugMode; return true; }

        y = cy + 62;
        if (inBox(mx, my, boxX, y + 2, boxSize)) { pendingBackoffItem = !pendingBackoffItem; return true; }
        y += 18;
        if (inBox(mx, my, boxX, y + 2, boxSize)) { pendingBackoffFluid = !pendingBackoffFluid; return true; }
        y += 18;
        if (inBox(mx, my, boxX, y + 2, boxSize)) { pendingBackoffEnergy = !pendingBackoffEnergy; return true; }
        y += 18;
        if (inBox(mx, my, boxX, y + 2, boxSize)) { pendingBackoffChemical = !pendingBackoffChemical; return true; }
        y += 18;
        if (inBox(mx, my, boxX, y + 2, boxSize)) { pendingBackoffSource = !pendingBackoffSource; return true; }

        return false;
    }

    private boolean handleUpgradesClick(double mx, double my, int cx, int cy, int cw) {
        int y = cy;
        for (int tier = 0; tier < 5; tier++) {
            if (mx >= cx && mx < cx + cw && my >= y && my < y + 16) {
                stashExpandedTier();
                expandedTier = expandedTier == tier ? -1 : tier;
                buildTab();
                return true;
            }
            y += 18;
            if (tier == expandedTier) {
                y += 3 * 22 + 4;
            }
        }
        return false;
    }

    private boolean inBox(double mx, double my, int bx, int by, int size) {
        return mx >= bx && mx < bx + size && my >= by && my < by + size;
    }

    private void stashCurrentTab() {
        switch (currentTab) {
            case COMMON -> {
                if (backoffMaxTicksBox != null) {
                    pendingBackoffMaxTicks = parseIntClamped(backoffMaxTicksBox.getValue(), 1, 200, pendingBackoffMaxTicks);
                }
            }
            case CLIENT -> {
                if (maxRenderedNodesBox != null) {
                    pendingMaxRenderedNodes = parseIntClamped(maxRenderedNodesBox.getValue(), 1, Integer.MAX_VALUE, pendingMaxRenderedNodes);
                }
                if (maxVisibleNodesBox != null) {
                    pendingMaxVisibleNodes = parseIntClamped(maxVisibleNodesBox.getValue(), 0, Integer.MAX_VALUE, pendingMaxVisibleNodes);
                }
            }
            case UPGRADES -> stashExpandedTier();
        }
    }

    private void stashExpandedTier() {
        if (expandedTier < 0 || expandedTier >= 5) return;
        if (upgradeBoxes[0] == null) return;

        int[] vals = new int[6];
        TierLimits old = pendingTiers[expandedTier];
        int[] defaults = { old.minTicks(), old.itemBatch(), old.fluidBatch(), old.energyBatch(), old.chemicalBatch(), old.sourceBatch() };

        for (int i = 0; i < 6; i++) {
            vals[i] = parseIntOr(upgradeBoxes[i].getValue(), defaults[i]);
        }
        pendingTiers[expandedTier] = new TierLimits(vals[0], vals[1], vals[2], vals[3], vals[4], vals[5]);
    }

    private void save() {
        stashCurrentTab();

        if (canEditServerConfig) {
            Config.dropNodeItemSpec.set(pendingDropNodeItem);
            Config.debugModeSpec.set(pendingDebugMode);
            Config.backoffItemSpec.set(pendingBackoffItem);
            Config.backoffFluidSpec.set(pendingBackoffFluid);
            Config.backoffEnergySpec.set(pendingBackoffEnergy);
            Config.backoffChemicalSpec.set(pendingBackoffChemical);
            Config.backoffSourceSpec.set(pendingBackoffSource);
            Config.backoffMaxTicksSpec.set(pendingBackoffMaxTicks);
            Config.refresh();

            for (int i = 0; i < 5; i++) {
                TierLimits t = pendingTiers[i];
                pendingTiers[i] = new TierLimits(
                        Math.max(1, t.minTicks()),
                        Math.max(1, t.itemBatch()),
                        Math.max(1, t.fluidBatch()),
                        Math.max(0, t.energyBatch()),
                        Math.max(1, t.chemicalBatch()),
                        Math.max(1, t.sourceBatch())
                );
                UpgradeLimitsConfig.setTier(i, pendingTiers[i]);
            }
            UpgradeLimitsConfig.save();
            Config.SPEC.save();
        }

        ClientConfig.maxRenderedNodesSpec.set(pendingMaxRenderedNodes);
        ClientConfig.maxVisibleNodesSpec.set(pendingMaxVisibleNodes);
        ClientConfig.themeSpec.set(pendingTheme);
        ClientConfig.refresh();
        ThemeState.setTheme(Themes.byId(pendingTheme));
        ClientConfig.SPEC.save();

        minecraft.setScreen(parent);
    }

    private void cancel() {
        minecraft.setScreen(parent);
    }

    private int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int parseIntClamped(String s, int min, int max, int fallback) {
        int v = parseIntOr(s, fallback);
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        EditBox focused = findFocusedEditBox();
        if (focused != null) {
            if (keyCode == 256) {
                focused.setValue(editStartValue);
                focused.setFocused(false);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                focused.setFocused(false);
                return true;
            }
            return focused.keyPressed(event);
        }
        if (keyCode == 256) {
            cancel();
            return true;
        }
        return super.keyPressed(event);
    }

    private EditBox findFocusedEditBox() {
        if (backoffMaxTicksBox != null && backoffMaxTicksBox.isFocused()) return backoffMaxTicksBox;
        if (maxRenderedNodesBox != null && maxRenderedNodesBox.isFocused()) return maxRenderedNodesBox;
        if (maxVisibleNodesBox != null && maxVisibleNodesBox.isFocused()) return maxVisibleNodesBox;
        for (EditBox box : upgradeBoxes) {
            if (box != null && box.isFocused()) return box;
        }
        return null;
    }

    private void unfocusEditBoxes() {
        if (backoffMaxTicksBox != null) backoffMaxTicksBox.setFocused(false);
        if (maxRenderedNodesBox != null) maxRenderedNodesBox.setFocused(false);
        if (maxVisibleNodesBox != null) maxVisibleNodesBox.setFocused(false);
        for (EditBox box : upgradeBoxes) {
            if (box != null) box.setFocused(false);
        }
    }
}
