package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.client.ClientControls;
import me.almana.logisticsnetworks.client.GuiGraphics;
import me.almana.logisticsnetworks.client.LegacyContainerScreen;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.client.theme.ThemeState;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.RackLinkStatus;
import me.almana.logisticsnetworks.data.ServerRackConfig;
import me.almana.logisticsnetworks.menu.ServerRackMenu;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload.NetworkEntry;
import me.almana.logisticsnetworks.network.SyncServerRackPayload;
import me.almana.logisticsnetworks.network.SyncServerRackPayload.Side;
import me.almana.logisticsnetworks.network.UpdateServerRackPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ServerRackScreen extends LegacyContainerScreen<ServerRackMenu> {

    private static final int GUI_W = 300;
    private static final int GUI_H = 140;
    private static final int COL_W = 118;
    private static final int HEADER_Y = 24;
    private static final int ROW_Y = 52;
    private static final int ROW_GAP = 26;
    private static final int PILL_H = 16;
    private static final int ARROW_W = 20;
    private static final boolean[] SIDES = { true, false };

    private ServerRackConfig config = ServerRackConfig.EMPTY;
    private Side leftSide = Side.EMPTY;
    private Side rightSide = Side.EMPTY;
    private List<NetworkEntry> networks = List.of();
    private NetworkDropdown leftDropdown;
    private NetworkDropdown rightDropdown;
    private int gridRow = -1;
    private boolean gridLeft;

    public ServerRackScreen(ServerRackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GUI_W, GUI_H);
        this.inventoryLabelY = 10_000;
        this.titleLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        leftDropdown = new NetworkDropdown(font, columnX(true), topPos + HEADER_Y, COL_W,
                id -> send(config.withSide(true, Optional.of(id))));
        rightDropdown = new NetworkDropdown(font, columnX(false), topPos + HEADER_Y, COL_W,
                id -> send(config.withSide(false, Optional.of(id))));
        gridRow = -1;
    }

    public void receiveNetworkList(List<NetworkEntry> list) {
        networks = List.copyOf(list);
    }

    public void receiveState(SyncServerRackPayload payload) {
        if (payload.rackPos().equals(menu.getRackPos())) {
            config = payload.config();
            leftSide = payload.left();
            rightSide = payload.right();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Theme t = ThemeState.active();
        leftDropdown.renderPanel(g, t, networks, config.right().orElse(null), mx, my, pt);
        rightDropdown.renderPanel(g, t, networks, config.left().orElse(null), mx, my, pt);
        if (gridRow >= 0) {
            RackChannelGrid.render(g, font, t, gridX(), gridY(), config.rows().get(gridRow).channel(gridLeft),
                    side(gridLeft), mx, my);
        }
        if (!popupOpen()) {
            renderArrowTooltip(g, mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        // Popups block background hover
        if (popupOpen()) {
            mx = Integer.MIN_VALUE;
            my = Integer.MIN_VALUE;
        }
        Theme t = ThemeState.active();
        ThemePaint.window(g, leftPos, topPos, GUI_W, GUI_H, t);
        ThemePaint.drawCentered(g, font, title, leftPos + GUI_W / 2, topPos + 8, t.accent());
        for (boolean left : SIDES) {
            Side side = side(left);
            dropdown(left).renderHeader(g, t, headerLabel(left), side.exists() ? side.color() : -1, mx, my);
            for (int row = 0; row < ServerRackConfig.ROWS; row++) {
                renderPill(g, t, left, row, mx, my);
            }
        }
        for (int row = 0; row < ServerRackConfig.ROWS; row++) {
            renderArrow(g, t, row, mx, my);
        }
    }

    private void renderPill(GuiGraphics g, Theme t, boolean left, int row, int mx, int my) {
        int channel = config.rows().get(row).channel(left);
        Side side = side(left);
        ChannelType type = side.type(channel);
        String name = side.channelNames().get(channel);
        String detail = !name.isEmpty() ? name
                : type != null ? typeLabel(type) : tr("gui.logisticsnetworks.server_rack.channel_empty");
        String label = font.plainSubstrByWidth(
                tr("gui.logisticsnetworks.computer.flow.channel_short", channel) + " · " + detail, COL_W - 8);
        int x = columnX(left);
        int y = rowY(row);
        boolean active = gridRow == row && gridLeft == left;
        ThemePaint.channelTab(g, font, x, y, COL_W, PILL_H, label, type, active, false,
                NetworkDropdown.hits(mx, my, x, y, COL_W, PILL_H), t);
    }

    private void renderArrow(GuiGraphics g, Theme t, int row, int mx, int my) {
        boolean linked = config.rows().get(row).linked();
        boolean broken = linked && problem(row) != null;
        int x = arrowX();
        int y = rowY(row);
        boolean hovered = NetworkDropdown.hits(mx, my, x, y, ARROW_W, PILL_H);
        int color = !linked ? (hovered ? t.text() : t.textSubtle()) : broken ? t.danger() : t.accent();
        if (linked) {
            int ly = y + PILL_H / 2;
            g.fill(columnX(true) + COL_W, ly, x, ly + 1, color);
            g.fill(x + ARROW_W, ly, columnX(false), ly + 1, color);
        }
        if (hovered) {
            ThemePaint.roundOutline(g, x, y, ARROW_W, PILL_H, 2, t.borderStrong(), t.sharpCorners());
        }
        ThemePaint.drawCentered(g, font, !linked ? "·" : broken ? "✖" : "↔", x + ARROW_W / 2, y + 4, color);
    }

    private void renderArrowTooltip(GuiGraphics g, int mx, int my) {
        for (int row = 0; row < ServerRackConfig.ROWS; row++) {
            if (!NetworkDropdown.hits(mx, my, arrowX(), rowY(row), ARROW_W, PILL_H)) {
                continue;
            }
            boolean linked = config.rows().get(row).linked();
            Component problem = linked ? problem(row) : null;
            List<Component> lines = new ArrayList<>();
            if (problem != null) {
                lines.add(problem.copy().withStyle(ChatFormatting.RED));
            }
            lines.add(Component.translatable(linked ? "gui.logisticsnetworks.server_rack.unlink"
                    : "gui.logisticsnetworks.server_rack.link").withStyle(ChatFormatting.GRAY));
            g.renderTooltip(font, lines, mx, my);
        }
    }

    @Nullable
    private Component problem(int row) {
        if (!config.bridges()) {
            return Component.translatable("gui.logisticsnetworks.server_rack.status.pick_networks");
        }
        if (!leftSide.exists() || !rightSide.exists()) {
            return Component.translatable("gui.logisticsnetworks.server_rack.status.unknown_network");
        }
        ServerRackConfig.Row r = config.rows().get(row);
        int leftExport = leftSide.exports()[r.left()];
        int leftImport = leftSide.imports()[r.left()];
        int rightExport = rightSide.exports()[r.right()];
        int rightImport = rightSide.imports()[r.right()];
        return switch (RackLinkStatus.of(leftExport, leftImport, rightExport, rightImport)) {
            case OK -> null;
            case LEFT_EMPTY -> Component.translatable("gui.logisticsnetworks.server_rack.status.empty",
                    leftSide.name(), r.left());
            case RIGHT_EMPTY -> Component.translatable("gui.logisticsnetworks.server_rack.status.empty",
                    rightSide.name(), r.right());
            case NO_SHARED_TYPE -> Component.translatable("gui.logisticsnetworks.server_rack.status.no_shared_type",
                    typeList(leftExport | leftImport), typeList(rightExport | rightImport));
            case NO_FLOW -> Component.translatable("gui.logisticsnetworks.server_rack.status.no_flow",
                    typeList((leftExport | leftImport) & (rightExport | rightImport)));
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ClientControls.resolveMouseAction(mx, my, button) != 0) {
            return super.mouseClicked(mx, my, button);
        }
        if (gridRow >= 0) {
            int channel = RackChannelGrid.hit(mx, my, gridX(), gridY());
            if (channel >= 0) {
                send(config.withRow(gridRow, config.rows().get(gridRow).withChannel(gridLeft, channel)));
            }
            gridRow = -1;
            return true;
        }
        boolean dropdownOpen = leftDropdown.isOpen() || rightDropdown.isOpen();
        boolean handled = leftDropdown.mouseClicked(mx, my, networks, config.right().orElse(null))
                | rightDropdown.mouseClicked(mx, my, networks, config.left().orElse(null));
        if (handled || dropdownOpen) {
            return true;
        }
        for (int row = 0; row < ServerRackConfig.ROWS; row++) {
            if (NetworkDropdown.hits(mx, my, arrowX(), rowY(row), ARROW_W, PILL_H)) {
                send(config.withRow(row, config.rows().get(row).toggled()));
                return true;
            }
            for (boolean left : SIDES) {
                if (NetworkDropdown.hits(mx, my, columnX(left), rowY(row), COL_W, PILL_H)) {
                    gridRow = row;
                    gridLeft = left;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && (gridRow >= 0 || leftDropdown.isOpen() || rightDropdown.isOpen())) {
            gridRow = -1;
            leftDropdown.close();
            rightDropdown.close();
            return true;
        }
        if (leftDropdown.typing() || rightDropdown.typing()) {
            leftDropdown.keyPressed(key, scan, mods);
            rightDropdown.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        return leftDropdown.charTyped(c, mods) || rightDropdown.charTyped(c, mods) || super.charTyped(c, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return leftDropdown.mouseScrolled(mx, my, sy, networks)
                || rightDropdown.mouseScrolled(mx, my, sy, networks)
                || super.mouseScrolled(mx, my, sx, sy);
    }

    private void send(ServerRackConfig next) {
        config = next;
        ClientPacketDistributor.sendToServer(new UpdateServerRackPayload(menu.getRackPos(), next));
    }

    private String headerLabel(boolean left) {
        Side side = side(left);
        if (side.exists()) {
            return side.name();
        }
        boolean set = (left ? config.left() : config.right()).isPresent();
        return tr(set ? "gui.logisticsnetworks.server_rack.unknown_network"
                : "gui.logisticsnetworks.server_rack.select_network");
    }

    private boolean popupOpen() {
        return gridRow >= 0 || leftDropdown.isOpen() || rightDropdown.isOpen();
    }

    private Side side(boolean left) {
        return left ? leftSide : rightSide;
    }

    private NetworkDropdown dropdown(boolean left) {
        return left ? leftDropdown : rightDropdown;
    }

    private int columnX(boolean left) {
        return leftPos + (left ? 10 : GUI_W - 10 - COL_W);
    }

    private int rowY(int row) {
        return topPos + ROW_Y + row * ROW_GAP;
    }

    private int arrowX() {
        return leftPos + (GUI_W - ARROW_W) / 2;
    }

    private int gridX() {
        return columnX(gridLeft) + (COL_W - RackChannelGrid.W) / 2;
    }

    // Flip above when no room
    private int gridY() {
        int below = rowY(gridRow) + PILL_H + 2;
        return below + RackChannelGrid.H <= topPos + GUI_H ? below : rowY(gridRow) - RackChannelGrid.H - 2;
    }

    private static String typeList(int mask) {
        List<String> names = new ArrayList<>();
        for (ChannelType type : ChannelType.values()) {
            if ((mask & 1 << type.ordinal()) != 0) {
                names.add(typeLabel(type));
            }
        }
        return String.join(", ", names);
    }

    private static String typeLabel(ChannelType type) {
        return tr("gui.logisticsnetworks.channel_type." + type.name().toLowerCase(Locale.ROOT));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
