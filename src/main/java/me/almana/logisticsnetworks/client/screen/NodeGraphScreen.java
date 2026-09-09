package me.almana.logisticsnetworks.client.screen;

import me.almana.logisticsnetworks.LogisticsNetworks;
import me.almana.logisticsnetworks.client.graph.GraphCanvas;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.data.graph.NetworkGraph;
import me.almana.logisticsnetworks.menu.NodeGraphMenu;
import me.almana.logisticsnetworks.network.MoveGraphVertexPayload;
import me.almana.logisticsnetworks.network.RequestNetworkGraphPayload;
import me.almana.logisticsnetworks.network.RequestOpenGraphPayload;
import me.almana.logisticsnetworks.network.ResetGraphLayoutPayload;
import me.almana.logisticsnetworks.network.ReturnToComputerPayload;
import me.almana.logisticsnetworks.network.SyncNetworkGraphPayload;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import me.almana.logisticsnetworks.client.ClientInput;
import me.almana.logisticsnetworks.client.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class NodeGraphScreen extends NodeEditorScreen<NodeGraphMenu> {
    private static final int PANEL_CLOSED_LEFT = -264;
    private static final int PANEL_OPEN_LEFT = 8;
    private static final int PANEL_CANVAS_GAP = 272;
    private static final int PANEL_TOP = 54;
    private static final long PANEL_ANIMATION_MILLIS = 450;
    private static final Identifier PREVIOUS_ICON = Identifier.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/gui/graph_left.png");
    private static final Identifier NEXT_ICON = Identifier.fromNamespaceAndPath(
            LogisticsNetworks.MOD_ID, "textures/gui/graph_right.png");

    private final NodeGraphSession session;
    private final NodeGraphMenu graphMenu;
    private double uiScale = 1;
    private int refreshTicks;
    private SyncNetworkGraphPayload receivedSnapshot;
    private boolean openingSelection;
    private long selectionRequestedAt;
    private long panelAnimationStartedAt = -1;
    private Button previousButton;
    private Button nextButton;

    public NodeGraphScreen(NodeGraphMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        graphMenu = menu;
        session = NodeGraphSession.attach(this, menu.getGraphNetworkId());
    }

    @Override
    protected void init() {
        boolean startPanelAnimation = menu.getNode() != null && !session.editorOpen
                && panelAnimationStartedAt < 0;
        uiScale = Math.min(1.0, Math.min(width / 640.0, height / 360.0));
        width = (int) Math.ceil(width / uiScale);
        height = (int) Math.ceil(height / uiScale);
        super.init();
        if (startPanelAnimation) panelAnimationStartedAt = Util.getMillis();
        updatePanelLayout(Util.getMillis());
        applyMenuSnapshot();
        updateSelection();
        session.editorOpen = menu.getNode() != null;
        ClientPacketDistributor.sendToServer(new RequestNetworkGraphPayload(graphMenu.getGraphNetworkId()));
    }

    @Override
    protected void positionEditor() {
        leftPos = 8;
        topPos = PANEL_TOP;
    }

    @Override
    protected void rebuildPageLayout() {
        previousButton = null;
        nextButton = null;
        if (menu.getNode() != null) super.rebuildPageLayout();
        else clearWidgets();
        addButton(width - 224, 8, 48, "back", this::returnToComputer);
        addButton(width - 170, 8, 46, "fit", () -> canvas().fit());
        addButton(width - 118, 8, 110, "reset", this::resetLayout);
        if (menu.getNode() != null) {
            previousButton = addIconButton(leftPos + 6, topPos - 16, "previous", PREVIOUS_ICON,
                    () -> selectMember(-1));
            nextButton = addIconButton(leftPos + 232, topPos - 16, "next", NEXT_ICON,
                    () -> selectMember(1));
            updateMemberButtons();
        }
    }

    private GraphButton addButton(int x, int y, int w, String key, Runnable action) {
        return addRenderableWidget(new GraphButton(x, y, w, text(key), action, this::theme));
    }

    private Button addIconButton(int x, int y, String key, Identifier icon, Runnable action) {
        return addRenderableWidget(new GraphIconButton(x, y, text(key), action, this::theme, icon));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        applyMenuSnapshot();
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            ClientPacketDistributor.sendToServer(new RequestNetworkGraphPayload(graphMenu.getGraphNetworkId()));
        }
        if (openingSelection && System.currentTimeMillis() - selectionRequestedAt > 3000) {
            openingSelection = false;
        }
    }

    private void applyMenuSnapshot() {
        SyncNetworkGraphPayload snapshot = graphMenu.getGraphSnapshot();
        if (snapshot != null && snapshot != receivedSnapshot) {
            receivedSnapshot = snapshot;
            receiveGraph(snapshot);
        }
    }

    public void receiveGraph(SyncNetworkGraphPayload snapshot) {
        if (!snapshot.networkId().equals(graphMenu.getGraphNetworkId())) return;
        session.snapshot = snapshot;
        canvas().update(snapshot.nodes(), snapshot.positions());
        updateSelection();
        if (menu.getNode() != null && selectedNode() == null && !openingSelection) openSelection(null);
    }

    private void updateSelection() {
        GraphNode selected = selectedNode();
        canvas().setSelected(selected == null ? null : NetworkGraph.key(selected),
                selected == null ? null : selected.nodeId());
        if (selected != null) menu.getNode().setNodeLabel(selected.label());
        updateMemberButtons();
    }

    private void updateMemberButtons() {
        boolean visible = selectedMembers().size() > 1;
        if (previousButton != null) {
            previousButton.visible = visible;
            previousButton.active = visible;
        }
        if (nextButton != null) {
            nextButton.visible = visible;
            nextButton.active = visible;
        }
    }

    private GraphNode selectedNode() {
        if (menu.getNode() == null || session.snapshot == null) return null;
        UUID id = menu.getNode().getUUID();
        return session.snapshot.nodes().stream().filter(node -> node.nodeId().equals(id)).findFirst().orElse(null);
    }

    private GraphCanvas canvas() {
        return session.canvas;
    }

    private void updateCanvasBounds() {
        int x = menu.getNode() == null ? 8 : canvasLeft(leftPos);
        canvas().setBounds(x, 38, width - x - 8, height - 78);
    }

    private void updatePanelLayout(long now) {
        boolean transitioning = menu.getNode() != null && panelAnimationStartedAt >= 0;
        if (transitioning) {
            long elapsed = now - panelAnimationStartedAt;
            leftPos = panelLeft(elapsed);
            if (panelAnimationComplete(elapsed)) panelAnimationStartedAt = -1;
        } else {
            leftPos = PANEL_OPEN_LEFT;
        }
        if (previousButton != null) previousButton.setX(leftPos + 6);
        if (nextButton != null) nextButton.setX(leftPos + 232);
        updateCanvasBounds();
        if (transitioning) canvas().fit();
    }

    static int panelLeft(long elapsedMillis) {
        float progress = Math.max(0.0f, Math.min(1.0f, elapsedMillis / (float) PANEL_ANIMATION_MILLIS));
        float remaining = 1.0f - progress;
        float eased = 1.0f - remaining * remaining * remaining;
        return Math.round(PANEL_CLOSED_LEFT + (PANEL_OPEN_LEFT - PANEL_CLOSED_LEFT) * eased);
    }

    static int canvasLeft(int editorLeft) {
        return Math.max(8, editorLeft + PANEL_CANVAS_GAP);
    }

    static boolean panelAnimationComplete(long elapsedMillis) {
        return elapsedMillis >= PANEL_ANIMATION_MILLIS;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushMatrix();
        graphics.pose().scale((float) uiScale);
        int x = (int) (mouseX / uiScale);
        int y = (int) (mouseY / uiScale);
        super.extractRenderState(graphics, x, y, partialTick);
        canvas().renderTooltips(guiGraphics(graphics), font, x, y, theme());
        graphics.pose().popMatrix();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updatePanelLayout(Util.getMillis());
        graphics.pose().pushMatrix();
        graphics.pose().scale((float) uiScale);
        renderBg(guiGraphics(graphics), partialTick, (int) (mouseX / uiScale), (int) (mouseY / uiScale));
        graphics.pose().popMatrix();
    }

    @Override
    protected GuiGraphics guiGraphics(GuiGraphicsExtractor graphics) {
        return new GuiGraphics(graphics, uiScale);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, (int) Math.round(mouseX * uiScale), (int) Math.round(mouseY * uiScale));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        Theme theme = theme();
        graphics.fill(0, 0, width, height, theme.bg());
        int gridColor = 0x28000000 | (theme.border() & 0x00FFFFFF);
        for (int y = 0; y < height; y += 8) {
            graphics.fill(0, y, width, y + 1, gridColor);
        }
        updateCanvasBounds();
        int canvasX = menu.getNode() == null ? 8 : canvasLeft(leftPos);
        graphics.raw().enableScissor(canvasX, 38, width - 8, height - 40);
        canvas().render(graphics, font, mouseX, mouseY, theme, Util.getMillis());
        graphics.raw().disableScissor();
        drawChrome(graphics, theme);
        canvas().drawLegend(graphics, font, menu.getNode() == null ? 12 : canvasX, height - 31, theme);
        if (menu.getNode() != null) {
            graphics.fill(leftPos - 4, 36, leftPos + 262, height - 4, theme.surface());
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            drawMember(graphics, mouseX, mouseY, theme);
        }
    }

    private void drawChrome(GuiGraphics graphics, Theme theme) {
        graphics.fill(0, 0, width, 34, theme.surface());
        int unloaded = session.snapshot == null ? 0 : session.snapshot.totalNodes() - session.snapshot.nodes().size();
        Component status = session.snapshot == null ? text("loading") : unloaded > 0
                ? Component.translatable("gui.logisticsnetworks.graph.unloaded", unloaded)
                : session.snapshot.nodes().isEmpty() ? text("empty") : text("hint");
        graphics.drawString(font, status, menu.getNode() == null ? 12 : canvasLeft(leftPos), height - 15,
                theme.textMuted(), false);
    }

    private void drawMember(GuiGraphics graphics, int mouseX, int mouseY, Theme theme) {
        GraphNode selected = selectedNode();
        if (selected == null) return;
        List<GraphNode> members = selectedMembers();
        int index = members.indexOf(selected) + 1;
        Component heading = Component.translatable("gui.logisticsnetworks.graph.selected_member", index, members.size());
        ThemePaint.drawCentered(graphics, font, heading, leftPos + 128, topPos - font.lineHeight - 2, theme.text());
        if (!selected.label().isEmpty() && topPos > 70) {
            graphics.drawString(font, text("shared"), leftPos + 6, 44, theme.textMuted(), false);
        }
        if (mouseX >= leftPos + 28 && mouseX < leftPos + 228
                && mouseY >= topPos - 26 && mouseY < topPos - 6) {
            graphics.renderTooltip(font, List.of(Component.literal(selected.blockName()),
                    Component.literal(selected.dimension() + " " + selected.attachedPos().toShortString()),
                    text(selected.label().isEmpty() ? "individual" : "shared")), mouseX, mouseY);
        }
    }

    private List<GraphNode> selectedMembers() {
        String key = canvas().getSelected();
        return canvas().getGraph().vertices().stream().filter(vertex -> vertex.key().equals(key))
                .findFirst().map(NetworkGraph.Vertex::members).orElse(List.of());
    }

    private void selectMember(int direction) {
        List<GraphNode> members = selectedMembers();
        if (members.size() < 2) return;
        int index = members.indexOf(selectedNode());
        openSelection(members.get(Math.floorMod(index + direction, members.size())).nodeId());
    }

    void selectVertex(String key) {
        if (key == null) {
            if (menu.getNode() != null) openSelection(null);
            return;
        }
        canvas().getGraph().vertices().stream().filter(vertex -> vertex.key().equals(key)).findFirst()
                .ifPresent(vertex -> openSelection(vertex.members().getFirst().nodeId()));
    }

    private void openSelection(UUID nodeId) {
        if (openingSelection || !menu.getCarried().isEmpty()) return;
        if (menu.getNode() != null && menu.getNode().getUUID().equals(nodeId)) return;
        commitPendingEdits();
        openingSelection = true;
        selectionRequestedAt = System.currentTimeMillis();
        ClientPacketDistributor.sendToServer(new RequestOpenGraphPayload(graphMenu.getComputerPos(),
                graphMenu.getComputerDimension(), graphMenu.getGraphNetworkId(), Optional.ofNullable(nodeId),
                getSelectedChannel()));
    }

    void moveVertex(String key, GraphPosition position) {
        ClientPacketDistributor.sendToServer(new MoveGraphVertexPayload(graphMenu.getGraphNetworkId(), key,
                position.x(), position.y()));
    }

    private void resetLayout() {
        if (openingSelection || !menu.getCarried().isEmpty()) return;
        ClientPacketDistributor.sendToServer(new ResetGraphLayoutPayload(graphMenu.getGraphNetworkId()));
    }

    private void returnToComputer() {
        if (openingSelection || !menu.getCarried().isEmpty()) return;
        commitPendingEdits();
        NodeGraphSession.returnToComputer(graphMenu.getGraphNetworkId());
        ClientPacketDistributor.sendToServer(new ReturnToComputerPayload(graphMenu.getGraphNetworkId()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX;
        double y = mouseY;
        if (openingSelection) return true;
        if (hasEditorOverlay()) {
            super.mouseClicked(x, y, button);
            return true;
        }
        for (var child : children()) {
            if (child instanceof Button control && control.mouseClicked(ClientInput.mouse(x, y, button), false)) {
                setFocused(control);
                return true;
            }
        }
        if (!menu.getCarried().isEmpty() && (menu.getNode() == null || x >= 270)) return true;
        if (canvas().mouseClicked(x, y, button)) return true;
        return menu.getNode() != null && super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        double x = mouseX;
        double y = mouseY;
        if (hasEditorOverlay()) {
            super.mouseDragged(x, y, button, deltaX, deltaY);
            return true;
        }
        if (canvas().mouseDragged(x, y, button, deltaX, deltaY)) return true;
        return menu.getNode() != null && super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double x = mouseX;
        double y = mouseY;
        if (hasEditorOverlay()) {
            super.mouseReleased(x, y, button);
            return true;
        }
        if (canvas().mouseReleased(x, y, button)) return true;
        return menu.getNode() != null && super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        double x = mouseX / uiScale;
        double y = mouseY / uiScale;
        if (canvas().mouseScrolled(x, y, deltaY)) return true;
        return menu.getNode() != null && super.mouseScrolled(x, y, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent scaled = scaledMouse(event);
        if (openingSelection) return true;
        if (!hasEditorOverlay()) return super.mouseClicked(scaled, doubleClick);
        if (!super.mouseClicked(scaled.x(), scaled.y(), scaled.button())) {
            var field = editorTextField();
            if (field != null && field.mouseClicked(scaled, doubleClick) && field.shouldTakeFocusAfterInteraction()) {
                setFocused(field);
                if (scaled.button() == 0) setDragging(true);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent scaled = scaledMouse(event);
        if (!hasEditorOverlay()) return super.mouseReleased(scaled);
        boolean handled = super.mouseReleased(scaled.x(), scaled.y(), scaled.button());
        if (scaled.button() == 0 && isDragging()) {
            setDragging(false);
            var field = editorTextField();
            if (!handled && field != null && getFocused() == field) field.mouseReleased(scaled);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        MouseButtonEvent scaled = scaledMouse(event);
        double dx = deltaX / uiScale;
        double dy = deltaY / uiScale;
        if (!hasEditorOverlay()) return super.mouseDragged(scaled, dx, dy);
        if (!super.mouseDragged(scaled.x(), scaled.y(), scaled.button(), dx, dy)) {
            var field = editorTextField();
            if (field != null && getFocused() == field && isDragging() && scaled.button() == 0) {
                field.mouseDragged(scaled, dx, dy);
            }
        }
        return true;
    }

    private MouseButtonEvent scaledMouse(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / uiScale, event.y() / uiScale, event.buttonInfo());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (openingSelection) return true;
        return super.keyPressed(event);
    }

    @Override
    protected boolean handleUnhandledKey(int key, int scan, int modifiers) {
        if (hasEditorOverlay()) return true;
        KeyEvent event = ClientInput.key(key, scan, modifiers);
        if (key == 258 || getFocused() instanceof Button && (event.isSelection() || key >= 262 && key <= 265)) {
            handleScreenKey(event);
        }
        return true;
    }

    @Override
    protected boolean handleInteraction(double mouseX, double mouseY, int action) {
        return menu.getNode() != null && super.handleInteraction(mouseX, mouseY, action);
    }

    @Override
    protected double editorMouseX() {
        return super.editorMouseX() / uiScale;
    }

    @Override
    protected double editorMouseY() {
        return super.editorMouseY() / uiScale;
    }

    @Override
    public void onClose() {
        returnToComputer();
    }

    @Override
    public Rect2i getFilterSlotArea(int slot) {
        Rect2i area = super.getFilterSlotArea(slot);
        return new Rect2i((int) (area.getX() * uiScale), (int) (area.getY() * uiScale),
                (int) Math.ceil(area.getWidth() * uiScale), (int) Math.ceil(area.getHeight() * uiScale));
    }

    private static Component text(String key) {
        return Component.translatable("gui.logisticsnetworks.graph." + key);
    }

    public static void clearSession() {
        NodeGraphSession.clear();
    }

    private static final class GraphButton extends Button {
        private final Supplier<Theme> theme;

        private GraphButton(int x, int y, int width, Component message, Runnable action,
                            Supplier<Theme> theme) {
            super(x, y, width, 20, message, button -> action.run(), DEFAULT_NARRATION);
            this.theme = theme;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor raw, int mouseX, int mouseY, float partialTick) {
            GuiGraphics graphics = new GuiGraphics(raw);
            ThemePaint.button(graphics, Minecraft.getInstance().font, getX(), getY(), getWidth(), getHeight(),
                    getMessage().getString(), isHoveredOrFocused(), theme.get());
        }
    }

    private static final class GraphIconButton extends Button {
        private final Supplier<Theme> theme;
        private final Identifier icon;

        private GraphIconButton(int x, int y, Component message, Runnable action, Supplier<Theme> theme,
                                Identifier icon) {
            super(x, y, 18, 18, message, button -> action.run(), DEFAULT_NARRATION);
            this.theme = theme;
            this.icon = icon;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor raw, int mouseX, int mouseY, float partialTick) {
            Theme currentTheme = theme.get();
            int color = isHoveredOrFocused() ? currentTheme.text() : currentTheme.accent();
            raw.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, icon,
                    getX() + 1, getY() + 1, 0, 0, 16, 16, 512, 512, 512, 512, color);
        }
    }
}

