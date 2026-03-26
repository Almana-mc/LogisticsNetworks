package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.LegacyContainerScreen;
import me.almana.logisticsnetworks.menu.PatternSetterMenu;
import me.almana.logisticsnetworks.network.ApplyPatternPayload;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class PatternSetterScreen extends LegacyContainerScreen<PatternSetterMenu> {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 152;

    private static final int COLOR_BG = 0xFF161616;
    private static final int COLOR_PANEL = 0xFF1F1F1F;
    private static final int COLOR_BORDER = 0xFF3A3A3A;
    private static final int COLOR_SLOT = 0xFF2A2A2A;
    private static final int COLOR_SLOT_BORDER = 0xFF444444;
    private static final int COLOR_BTN = 0xFF2E2E2E;
    private static final int COLOR_BTN_HOVER = 0xFF3A3A3A;
    private static final int COLOR_BTN_BORDER = 0xFF505050;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF999999;
    private static final int COLOR_SUCCESS = 0xFF44CC44;

    private static final int SLOT_X = 62;
    private static final int PATTERN_SLOT_Y = 28;
    private static final int FILTER_SLOT_Y = 52;

    private static final int BTN_WIDTH = 38;
    private static final int BTN_HEIGHT = 14;
    private static final int BTN_Y = 38;
    private static final int BTN_X = SLOT_X + 22;
    private static final int BTN_GAP = 3;

    private static final int MULT_FIELD_W = 30;
    private static final int MULT_FIELD_H = 12;
    private static final int MULT_FIELD_X = BTN_X + (BTN_WIDTH * 2 + BTN_GAP - MULT_FIELD_W) / 2;
    private static final int MULT_FIELD_Y = BTN_Y + BTN_HEIGHT + 3;

    private EditBox multiplierField;
    private int feedbackTimer = 0;

    public PatternSetterScreen(PatternSetterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);
        this.inventoryLabelY = 10_000;
        this.titleLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        multiplierField = new EditBox(font, leftPos + MULT_FIELD_X + 1, topPos + MULT_FIELD_Y + 2,
                MULT_FIELD_W - 2, MULT_FIELD_H - 2, Component.empty());
        multiplierField.setMaxLength(5);
        multiplierField.setValue("1");
        multiplierField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        multiplierField.setTextColor(COLOR_TEXT);
        multiplierField.setBordered(false);
        addRenderableWidget(multiplierField);
    }

    private int getMultiplier() {
        try {
            int val = Integer.parseInt(multiplierField.getValue());
            return Math.max(1, Math.min(val, 10_000));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (feedbackTimer > 0) feedbackTimer--;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int relX = (int) mouseX - leftPos;
            int relY = (int) mouseY - topPos;

            // Input button
            if (relX >= BTN_X && relX < BTN_X + BTN_WIDTH && relY >= BTN_Y && relY < BTN_Y + BTN_HEIGHT) {
                ClientPacketDistributor.sendToServer(new ApplyPatternPayload(false, getMultiplier()));
                feedbackTimer = 40;
                return true;
            }
            // Output button
            int btn2X = BTN_X + BTN_WIDTH + BTN_GAP;
            if (relX >= btn2X && relX < btn2X + BTN_WIDTH && relY >= BTN_Y && relY < BTN_Y + BTN_HEIGHT) {
                ClientPacketDistributor.sendToServer(new ApplyPatternPayload(true, getMultiplier()));
                feedbackTimer = 40;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int lp = leftPos;
        int tp = topPos;

        // Main background
        graphics.fill(lp, tp, lp + GUI_WIDTH, tp + GUI_HEIGHT, COLOR_BG);
        graphics.fill(lp + 1, tp + 1, lp + GUI_WIDTH - 1, tp + GUI_HEIGHT - 1, COLOR_PANEL);

        // Border
        graphics.fill(lp, tp, lp + GUI_WIDTH, tp + 1, COLOR_BORDER);
        graphics.fill(lp, tp + GUI_HEIGHT - 1, lp + GUI_WIDTH, tp + GUI_HEIGHT, COLOR_BORDER);
        graphics.fill(lp, tp, lp + 1, tp + GUI_HEIGHT, COLOR_BORDER);
        graphics.fill(lp + GUI_WIDTH - 1, tp, lp + GUI_WIDTH, tp + GUI_HEIGHT, COLOR_BORDER);

        // Title
        graphics.drawCenteredString(font,
                Component.translatable("gui.logisticsnetworks.pattern_setter.title"),
                lp + GUI_WIDTH / 2, tp + 6, COLOR_TEXT);

        // Multiplier field background + label
        graphics.drawString(font, "x", lp + MULT_FIELD_X + MULT_FIELD_W + 2, tp + MULT_FIELD_Y + 2, COLOR_MUTED, false);
        graphics.fill(lp + MULT_FIELD_X - 1, tp + MULT_FIELD_Y - 1,
                lp + MULT_FIELD_X + MULT_FIELD_W + 1, tp + MULT_FIELD_Y + MULT_FIELD_H + 1, COLOR_SLOT_BORDER);
        graphics.fill(lp + MULT_FIELD_X, tp + MULT_FIELD_Y,
                lp + MULT_FIELD_X + MULT_FIELD_W, tp + MULT_FIELD_Y + MULT_FIELD_H, COLOR_SLOT);

        // Slot labels
        graphics.drawString(font,
                Component.translatable("gui.logisticsnetworks.pattern_setter.pattern_label"),
                lp + 8, tp + PATTERN_SLOT_Y + 4, COLOR_MUTED, false);
        graphics.drawString(font,
                Component.translatable("gui.logisticsnetworks.pattern_setter.filter_label"),
                lp + 8, tp + FILTER_SLOT_Y + 4, COLOR_MUTED, false);

        // Slot backgrounds
        renderSlotBackground(graphics, lp + SLOT_X, tp + PATTERN_SLOT_Y);
        renderSlotBackground(graphics, lp + SLOT_X, tp + FILTER_SLOT_Y);

        // Buttons
        int relMX = mouseX - lp;
        int relMY = mouseY - tp;

        renderButton(graphics, lp + BTN_X, tp + BTN_Y,
                Component.translatable("gui.logisticsnetworks.pattern_setter.input"),
                relMX >= BTN_X && relMX < BTN_X + BTN_WIDTH && relMY >= BTN_Y && relMY < BTN_Y + BTN_HEIGHT);

        int btn2X = BTN_X + BTN_WIDTH + BTN_GAP;
        renderButton(graphics, lp + btn2X, tp + BTN_Y,
                Component.translatable("gui.logisticsnetworks.pattern_setter.output"),
                relMX >= btn2X && relMX < btn2X + BTN_WIDTH && relMY >= BTN_Y && relMY < BTN_Y + BTN_HEIGHT);

        // Feedback text
        if (feedbackTimer > 0) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.logisticsnetworks.pattern_setter.applied"),
                    lp + GUI_WIDTH / 2, tp + 70, COLOR_SUCCESS);
        }

        // Player inventory label
        graphics.drawString(font, Component.translatable("container.inventory"),
                lp + 8, tp + 73, COLOR_MUTED, false);

        // Player inventory slot backgrounds
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                renderSlotBackground(graphics, lp + 8 + c * 18, tp + 84 + r * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            renderSlotBackground(graphics, lp + 8 + c * 18, tp + 84 + 58);
        }
    }

    private void renderButton(GuiGraphics graphics, int x, int y, Component label, boolean hovered) {
        int bg = hovered ? COLOR_BTN_HOVER : COLOR_BTN;
        graphics.fill(x, y, x + BTN_WIDTH, y + BTN_HEIGHT, COLOR_BTN_BORDER);
        graphics.fill(x + 1, y + 1, x + BTN_WIDTH - 1, y + BTN_HEIGHT - 1, bg);
        graphics.drawCenteredString(font, label, x + BTN_WIDTH / 2, y + (BTN_HEIGHT - 8) / 2, COLOR_TEXT);
    }

    private void renderSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT);
    }
}
