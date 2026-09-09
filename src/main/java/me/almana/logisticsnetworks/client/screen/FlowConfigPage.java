package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.ClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

final class FlowConfigPage {
    private static final String PREFIX = "gui.logisticsnetworks.config.client.";
    private final List<NumberOption> numbers = List.of(
            new NumberOption(ClientConfig.flowLineThicknessSpec, 1),
            new NumberOption(ClientConfig.flowLineSpeedSpec, 2),
            new NumberOption(ClientConfig.flowLineOpacitySpec, 3),
            new NumberOption(ClientConfig.flowLinePulseSpacingSpec, 5),
            new NumberOption(ClientConfig.flowLinePulseLengthSpec, 6));
    private final List<ToggleOption> toggles = List.of(
            new ToggleOption(ClientConfig.flowLinesEnabledSpec, 0),
            new ToggleOption(ClientConfig.flowLinePulsesSpec, 4),
            new ToggleOption(ClientConfig.flowLinesThroughBlocksSpec, 7));

    List<AbstractWidget> build(Font font, int x, int y, int width) {
        List<AbstractWidget> widgets = new ArrayList<>();
        for (NumberOption option : numbers) {
            EditBox box = new EditBox(font, x + 150, y + option.row * 18, 80, 14, label(option.spec));
            box.setBordered(false);
            box.setMaxLength(8);
            box.setFilter(text -> text.matches("[0-9]*\\.?[0-9]*"));
            box.setValue(option.pending);
            box.setTooltip(tooltip(option.spec));
            box.setResponder(text -> {
                option.pending = text;
            });
            option.box = box;
            widgets.add(box);
        }
        for (ToggleOption option : toggles) {
            Button button = Button.builder(option.message(), clicked -> {
                option.pending = !option.pending;
                clicked.setMessage(option.message());
            }).bounds(x, y + option.row * 18, width, 16).tooltip(tooltip(option.spec))
                    .createNarration(message -> label(option.spec).copy().append(": ").append(message.get())).build();
            option.button = button;
            widgets.add(button);
        }
        return widgets;
    }

    void render(ModConfigScreen screen, GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int mouseX, int mouseY) {
        for (NumberOption option : numbers) {
            int rowY = y + option.row * 18;
            graphics.text(font, label(option.spec), x, rowY + 3, option.valid() ? 0xFF0A0400 : 0xFF9C2525, false);
            screen.renderUnderline(graphics, x + 150, rowY + 14, 80);
            screen.renderEditBox(graphics, option.box);
        }
        for (ToggleOption option : toggles) {
            int rowY = y + option.row * 18;
            screen.renderCheckbox(graphics, x, rowY, width, label(option.spec), option.pending, mouseX, mouseY, false);
            if (option.button.isFocused()) graphics.outline(x + width - 16, rowY, 13, 13, 0xFFB89A6A);
        }
    }

    void renderTooltips(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        for (NumberOption option : numbers) {
            if (option.box.isMouseOver(mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(description(option.spec)), mouseX, mouseY);
                return;
            }
        }
        for (ToggleOption option : toggles) {
            if (option.button.isMouseOver(mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(description(option.spec)), mouseX, mouseY);
                return;
            }
        }
    }

    boolean valid() {
        return numbers.stream().allMatch(NumberOption::valid);
    }

    void save() {
        numbers.forEach(option -> option.spec.set(Double.parseDouble(option.pending)));
        toggles.forEach(option -> option.spec.set(option.pending));
    }

    EditBox focused() {
        return numbers.stream().map(option -> option.box).filter(box -> box != null && box.isFocused())
                .findFirst().orElse(null);
    }

    void unfocus() {
        numbers.forEach(option -> {
            if (option.box != null) option.box.setFocused(false);
        });
    }

    private static Component label(ModConfigSpec.ConfigValue<?> spec) {
        return Component.translatable(PREFIX + spec.getPath().getLast());
    }

    private static Tooltip tooltip(ModConfigSpec.ConfigValue<?> spec) {
        return Tooltip.create(description(spec));
    }

    private static Component description(ModConfigSpec.ConfigValue<?> spec) {
        return Component.translatable(PREFIX + spec.getPath().getLast() + ".tooltip");
    }

    private static final class NumberOption {
        final ModConfigSpec.DoubleValue spec;
        final int row;
        String pending;
        EditBox box;

        NumberOption(ModConfigSpec.DoubleValue spec, int row) {
            this.spec = spec;
            this.row = row;
            pending = Double.toString(spec.get());
        }

        boolean valid() {
            try {
                double number = Double.parseDouble(pending);
                return Double.isFinite(number) && spec.getSpec().test(number);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
    }

    private static final class ToggleOption {
        final ModConfigSpec.BooleanValue spec;
        final int row;
        boolean pending;
        Button button;

        ToggleOption(ModConfigSpec.BooleanValue spec, int row) {
            this.spec = spec;
            this.row = row;
            pending = spec.get();
        }

        Component message() {
            return Component.translatable("gui.logisticsnetworks.config." + (pending ? "enabled" : "disabled"));
        }
    }
}
