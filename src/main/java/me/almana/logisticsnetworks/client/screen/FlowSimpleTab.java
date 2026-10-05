package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.component.WrenchFlow;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.entity.LogisticsNodeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class FlowSimpleTab implements WrenchTab {
    private static final ChannelType[] TYPES = ChannelType.values();
    private static final int W = 290;
    private static final int ROW_H = 14;
    private static final int CHANNEL_W = 22;
    private static final int NETWORK_X = 60;

    private final FlowAdvancedTab advanced;
    private final List<Network> networks;
    boolean enabled;
    boolean offscreen;
    int types;
    int channels;
    private int network;
    private int x;
    private int y;

    FlowSimpleTab(WrenchFlow flow, FlowAdvancedTab advanced) {
        this.advanced = advanced;
        networks = nearbyNetworks(flow.network());
        load(flow);
    }

    private void load(WrenchFlow flow) {
        enabled = flow.enabled();
        offscreen = flow.offscreen();
        types = flow.types();
        channels = flow.channels();
        network = 0;
        for (int i = 0; i < networks.size(); i++) {
            if (networks.get(i).id().equals(flow.network())) network = i;
        }
    }

    Optional<UUID> network() {
        return networks.get(network).id();
    }

    @Override
    public void place(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, Theme theme) {
        toggle(g, font, x, y, W, label("enabled"), enabled, mouseX, mouseY, theme);
        g.drawString(font, label("resources"), x, y + 20, theme.textMuted(), false);
        int[] typeX = typeXs();
        for (ChannelType type : TYPES) {
            int i = type.ordinal();
            boolean on = (types >> i & 1) != 0;
            toggle(g, font, typeX[i], y + 31, typeX[i + 1] - typeX[i] - 4, FlowAdvancedTab.typeName(type), on,
                    mouseX, mouseY, theme);
            if (on) g.fill(typeX[i] + 4, y + 36, typeX[i] + 8, y + 40, 0xFF000000 | advanced.color(type));
        }
        g.drawString(font, label("channels"), x, y + 51, theme.textMuted(), false);
        for (int i = 0; i < 9; i++) {
            toggle(g, font, channelX(i), y + 62, CHANNEL_W, String.valueOf(i + 1), (channels >> i & 1) != 0,
                    mouseX, mouseY, theme);
        }
        g.drawString(font, label("network"), x, y + 85, theme.textMuted(), false);
        Network shown = networks.get(network);
        ThemePaint.button(g, font, x + NETWORK_X, y + 82, W - NETWORK_X, ROW_H, "< " + shown.name() + " >",
                ColorPicker.inRect(mouseX, mouseY, x + NETWORK_X, y + 82, W - NETWORK_X, ROW_H), theme);
        g.fill(x + NETWORK_X + 4, y + 85, x + NETWORK_X + 12, y + 93, 0xFF000000 | shown.color());
        toggle(g, font, x, y + 102, W, label("offscreen"), offscreen, mouseX, mouseY, theme);
    }

    private static void toggle(GuiGraphics g, Font font, int x, int y, int width, String text, boolean on,
                               int mouseX, int mouseY, Theme theme) {
        ThemePaint.visibleToggle(g, font, x, y, width, ROW_H, text, on,
                ColorPicker.inRect(mouseX, mouseY, x, y, width, ROW_H), theme);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (ColorPicker.inRect(mouseX, mouseY, x, y, W, ROW_H)) enabled = !enabled;
        else if (ColorPicker.inRect(mouseX, mouseY, x, y + 102, W, ROW_H)) offscreen = !offscreen;
        else if (ColorPicker.inRect(mouseX, mouseY, x + NETWORK_X, y + 82, W - NETWORK_X, ROW_H)) {
            network = (network + 1) % networks.size();
        } else return toggleChip(mouseX, mouseY);
        return true;
    }

    private boolean toggleChip(double mouseX, double mouseY) {
        int[] typeX = typeXs();
        for (int i = 0; i < TYPES.length; i++) {
            if (ColorPicker.inRect(mouseX, mouseY, typeX[i], y + 31, typeX[i + 1] - typeX[i] - 4, ROW_H)) {
                types ^= 1 << i;
                return true;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (ColorPicker.inRect(mouseX, mouseY, channelX(i), y + 62, CHANNEL_W, ROW_H)) {
                channels ^= 1 << i;
                return true;
            }
        }
        return false;
    }

    @Override
    public void reset() {
        load(WrenchFlow.DEFAULT);
    }

    private int[] typeXs() {
        Font font = Minecraft.getInstance().font;
        int[] xs = new int[TYPES.length + 1];
        xs[0] = x;
        for (ChannelType type : TYPES) {
            xs[type.ordinal() + 1] = xs[type.ordinal()] + font.width(FlowAdvancedTab.typeName(type)) + 18;
        }
        return xs;
    }

    private int channelX(int channel) {
        return x + channel * (CHANNEL_W + 4);
    }

    private static String label(String key) {
        return Component.translatable(FlowAdvancedTab.PREFIX + key).getString();
    }

    private static List<Network> nearbyNetworks(Optional<UUID> saved) {
        Map<UUID, Network> found = new HashMap<>();
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity instanceof LogisticsNodeEntity node && node.getNetworkId() != null) {
                UUID id = node.getNetworkId();
                String name = node.getNetworkName().isEmpty() ? id.toString().substring(0, 8) : node.getNetworkName();
                found.putIfAbsent(id, new Network(Optional.of(id), name, node.getNetworkColor()));
            }
        }
        List<Network> result = new ArrayList<>();
        result.add(new Network(Optional.empty(), label("network.all"), 0xFFFFFF));
        found.values().stream().sorted(Comparator.comparing(Network::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(result::add);
        saved.filter(id -> !found.containsKey(id))
                .ifPresent(id -> result.add(new Network(saved, label("network.saved"), 0x808080)));
        return result;
    }

    private record Network(Optional<UUID> id, String name, int color) {
    }
}
