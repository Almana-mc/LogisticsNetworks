package me.almana.logisticsnetworks.client.graph;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.almana.logisticsnetworks.client.theme.Theme;
import me.almana.logisticsnetworks.client.theme.ThemePaint;
import me.almana.logisticsnetworks.data.ChannelMode;
import me.almana.logisticsnetworks.data.ChannelType;
import me.almana.logisticsnetworks.data.graph.GraphChannel;
import me.almana.logisticsnetworks.data.graph.GraphNode;
import me.almana.logisticsnetworks.data.graph.GraphPosition;
import me.almana.logisticsnetworks.data.graph.NetworkGraph;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class GraphCanvas {
    private static final int NODE_RADIUS = 22;
    private static final int NODE_HALF_HEIGHT = 19;
    private static final int DRAG_THRESHOLD = 4;
    private static final int MAX_TOOLTIP_MEMBERS = 8;
    private static final int MAX_TOOLTIP_ROUTES = 10;
    private final Consumer<String> selectionChanged;
    private final Consumer<Map<String, GraphPosition>> positionsMoved;
    private final GraphSelection selection = new GraphSelection();
    private final GraphCamera camera = new GraphCamera();
    private final EnumMap<ChannelType, ConnectionBatch> connections = new EnumMap<>(ChannelType.class);
    private final Map<String, List<ConnectionRef>> connectionRefs = new HashMap<>();
    private List<GraphNode> sourceNodes = List.of();
    private NetworkGraph graph = NetworkGraph.from(List.of());
    private Map<String, GraphPosition> positions = new LinkedHashMap<>();
    private String focused;
    private UUID selectedMember;
    private String pressedNode;
    private Map<String, GraphPosition> pressedPositions = Map.of();
    private double pressX;
    private double pressY;
    private double lastMouseX;
    private double lastMouseY;
    private boolean draggingNode;
    private boolean panning;
    private boolean boxSelecting;
    private boolean controlPressed;
    private boolean interactionMoved;
    private boolean fitPending;

    public GraphCanvas(Consumer<String> selectionChanged,
                       Consumer<Map<String, GraphPosition>> positionsMoved) {
        this.selectionChanged = selectionChanged;
        this.positionsMoved = positionsMoved;
        for (ChannelType type : ChannelType.values()) {
            connections.put(type, new ConnectionBatch(0));
        }
    }

    public void update(List<GraphNode> nodes, Map<String, GraphPosition> savedPositions) {
        List<GraphNode> nextNodes = List.copyOf(nodes);
        if (nextNodes.equals(sourceNodes)) {
            if (applySavedPositions(savedPositions)) {
                rebuildConnections();
            }
            return;
        }

        NetworkGraph oldGraph = graph;
        Map<String, GraphPosition> oldPositions = positions;
        boolean firstGraph = sourceNodes.isEmpty() && !nextNodes.isEmpty();
        sourceNodes = nextNodes;
        graph = NetworkGraph.from(nextNodes);

        Map<String, GraphPosition> nextPositions = new LinkedHashMap<>();
        copySavedPositions(savedPositions, nextPositions);
        copyStablePositions(oldPositions, nextPositions);
        inheritRegroupedPositions(oldGraph, oldPositions, nextPositions);
        positions = new LinkedHashMap<>(NetworkGraph.initialPositions(graph.vertices(), nextPositions));
        positions.keySet().retainAll(vertexKeys());
        if (pressedNode != null && !positions.containsKey(pressedNode)) {
            cancelInteraction();
        }
        selection.retain(nextNodes.stream().map(GraphNode::nodeId).toList());
        if (selectedMember != null) focused = keyForMember(selectedMember);
        if (focused != null && !positions.containsKey(focused)) focused = null;
        if (focused == null) selectedMember = null;
        rebuildConnections();
        if (firstGraph) {
            fitPending = true;
            fitIfReady();
        }
    }

    public void setBounds(int x, int y, int width, int height) {
        camera.setBounds(x, y, width, height);
        fitIfReady();
    }

    public void setSelected(String selected, UUID selectedMember) {
        focused = selected != null && positions.containsKey(selected) ? selected : null;
        this.selectedMember = focused == null ? null : selectedMember;
        NetworkGraph.Vertex vertex = vertex(focused);
        if (vertex != null) selection.initialize(memberIds(vertex));
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, Theme theme, long elapsedMillis) {
        if (camera.width() <= 0 || camera.height() <= 0) {
            return;
        }

        String hovered = findNode(mouseX, mouseY);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(camera.x() + camera.width() / 2.0f, camera.y() + camera.height() / 2.0f, 0.0f);
        pose.scale(camera.zoom(), camera.zoom(), 1.0f);
        pose.translate(-camera.centerX(), -camera.centerY(), 0.0f);
        drawConnections(graphics, theme, elapsedMillis);
        drawNodes(graphics, font, hovered, theme);
        graphics.flush();
        pose.popPose();
        if (boxSelecting && interactionMoved) drawSelectionBox(graphics, theme);
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY, Theme theme) {
        String hovered = findNode(mouseX, mouseY);
        if (hovered != null) {
            renderTooltipDetails(graphics, font, hovered, mouseX, mouseY, theme);
        }
    }

    public void drawLegend(GuiGraphics graphics, Font font, int x, int y, Theme theme) {
        int columnX = x;
        for (ChannelType type : ChannelType.values()) {
            Component label = channelName(type);
            graphics.fill(columnX, y + 3, columnX + 12, y + 5, color(type));
            graphics.drawString(font, label, columnX + 17, y, theme.textMuted(), false);
            columnX += font.width(label) + 27;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClicked(mouseX, mouseY, button, false, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean control, boolean shift) {
        if (button != 0 || !camera.contains(mouseX, mouseY)) {
            return false;
        }

        pressX = mouseX;
        pressY = mouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        pressedNode = findNode(mouseX, mouseY);
        pressedPositions = pressedNode == null ? Map.of() : dragPositions(pressedNode);
        draggingNode = false;
        interactionMoved = false;
        controlPressed = control;
        boxSelecting = pressedNode == null && shift;
        panning = pressedNode == null && !shift;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || (!panning && !boxSelecting && pressedNode == null)) {
            return false;
        }

        double deltaX = mouseX - pressX;
        double deltaY = mouseY - pressY;
        if (!interactionMoved && deltaX * deltaX + deltaY * deltaY < DRAG_THRESHOLD * DRAG_THRESHOLD) {
            return true;
        }
        interactionMoved = true;

        if (boxSelecting) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        if (panning) {
            camera.pan(mouseX - lastMouseX, mouseY - lastMouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        draggingNode = true;
        if (selection.state(memberIds(vertex(pressedNode))) == GraphSelection.State.NONE) {
            selection.click(memberIds(vertex(pressedNode)), controlPressed);
            pressedPositions = dragPositions(pressedNode);
        }
        for (Map.Entry<String, GraphPosition> entry : pressedPositions.entrySet()) {
            positions.put(entry.getKey(), new GraphPosition(
                    entry.getValue().x() + (float) deltaX / camera.zoom(),
                    entry.getValue().y() + (float) deltaY / camera.zoom()));
            updateConnections(entry.getKey());
        }
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || (!panning && !boxSelecting && pressedNode == null)) {
            return false;
        }

        if (!interactionMoved) {
            clickSelection(pressedNode);
        } else if (boxSelecting) {
            selection.box(nodesInBox(pressX, pressY, mouseX, mouseY), controlPressed);
        } else if (draggingNode) {
            Map<String, GraphPosition> moved = new LinkedHashMap<>();
            for (String key : pressedPositions.keySet()) moved.put(key, positions.get(key));
            positionsMoved.accept(moved);
        }
        pressedNode = null;
        pressedPositions = Map.of();
        draggingNode = false;
        panning = false;
        boxSelecting = false;
        controlPressed = false;
        interactionMoved = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!camera.contains(mouseX, mouseY) || amount == 0.0) {
            return false;
        }
        camera.zoomAt(mouseX, mouseY, amount);
        return true;
    }

    public void fit() {
        camera.fit(positions.values());
        fitPending = false;
    }

    public void reset() {
        camera.reset();
    }

    public NetworkGraph getGraph() {
        return graph;
    }

    public String getSelected() {
        return focused;
    }

    public Set<UUID> getSelectedNodes() {
        return selection.nodes();
    }

    public Set<String> getSelectedVertices() {
        Set<String> keys = new LinkedHashSet<>();
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            if (selection.state(memberIds(vertex)) != GraphSelection.State.NONE) keys.add(vertex.key());
        }
        return keys;
    }

    public void selectSameBlock(UUID source) {
        GraphNode selected = sourceNodes.stream().filter(node -> node.nodeId().equals(source)).findFirst().orElse(null);
        if (selected == null) return;
        selection.replace(sourceNodes.stream().filter(node -> node.blockName().equals(selected.blockName()))
                .map(GraphNode::nodeId).toList());
    }

    public Map<String, GraphPosition> distribute(GraphLayout.Axis axis) {
        Map<String, GraphPosition> moved = GraphLayout.distribute(positions, getSelectedVertices(), axis);
        positions.putAll(moved);
        moved.keySet().forEach(this::updateConnections);
        if (!moved.isEmpty()) positionsMoved.accept(moved);
        return moved;
    }

    public GraphPosition getPosition(String key) {
        return positions.get(key);
    }

    private boolean applySavedPositions(Map<String, GraphPosition> savedPositions) {
        boolean changed = false;
        for (Map.Entry<String, GraphPosition> entry : savedPositions.entrySet()) {
            if (!skipSavedPosition(entry.getKey()) && positions.containsKey(entry.getKey())
                    && !entry.getValue().equals(positions.get(entry.getKey()))) {
                positions.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private void copySavedPositions(Map<String, GraphPosition> savedPositions,
                                    Map<String, GraphPosition> target) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            GraphPosition position = savedPositions.get(vertex.key());
            if (position != null && !skipSavedPosition(vertex.key())) {
                target.put(vertex.key(), position);
            }
        }
    }

    private void copyStablePositions(Map<String, GraphPosition> oldPositions,
                                     Map<String, GraphPosition> target) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            if (!target.containsKey(vertex.key()) && oldPositions.containsKey(vertex.key())) {
                target.put(vertex.key(), oldPositions.get(vertex.key()));
            }
        }
    }

    private void inheritRegroupedPositions(NetworkGraph oldGraph,
                                           Map<String, GraphPosition> oldPositions,
                                           Map<String, GraphPosition> target) {
        Map<UUID, GraphPosition> memberPositions = new HashMap<>();
        for (NetworkGraph.Vertex vertex : oldGraph.vertices()) {
            GraphPosition position = oldPositions.get(vertex.key());
            if (position == null) {
                continue;
            }
            for (GraphNode member : vertex.members()) {
                memberPositions.put(member.nodeId(), position);
            }
        }

        Map<GraphPosition, Integer> inheritedCounts = new HashMap<>();
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            if (target.containsKey(vertex.key())) {
                continue;
            }
            GraphPosition inherited = averageMemberPosition(vertex, memberPositions);
            if (inherited == null) {
                continue;
            }
            int splitIndex = inheritedCounts.merge(inherited, 1, Integer::sum) - 1;
            target.put(vertex.key(), offsetSplit(inherited, splitIndex));
        }
    }

    private GraphPosition averageMemberPosition(NetworkGraph.Vertex vertex,
                                                Map<UUID, GraphPosition> memberPositions) {
        float totalX = 0.0f;
        float totalY = 0.0f;
        int count = 0;
        for (GraphNode member : vertex.members()) {
            GraphPosition position = memberPositions.get(member.nodeId());
            if (position != null) {
                totalX += position.x();
                totalY += position.y();
                count++;
            }
        }
        return count == 0 ? null : new GraphPosition(totalX / count, totalY / count);
    }

    private GraphPosition offsetSplit(GraphPosition position, int index) {
        if (index == 0) {
            return position;
        }
        double angle = (index - 1) * Math.PI / 3.0;
        float distance = 46.0f * (1 + (index - 1) / 6);
        return new GraphPosition(position.x() + (float) Math.cos(angle) * distance,
                position.y() + (float) Math.sin(angle) * distance);
    }

    private Set<String> vertexKeys() {
        Set<String> keys = new HashSet<>(graph.vertices().size());
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            keys.add(vertex.key());
        }
        return keys;
    }

    private void rebuildConnections() {
        connectionRefs.clear();
        EnumMap<ChannelType, Integer> capacities = new EnumMap<>(ChannelType.class);
        for (ChannelType type : ChannelType.values()) {
            capacities.put(type, 0);
        }
        for (NetworkGraph.Edge edge : graph.edges()) {
            capacities.put(edge.type(), capacities.get(edge.type()) + 1);
        }
        for (ChannelType type : ChannelType.values()) {
            connections.put(type, new ConnectionBatch(capacities.get(type)));
        }

        List<NetworkGraph.Edge> edges = graph.edges();
        int runStart = 0;
        while (runStart < edges.size()) {
            NetworkGraph.Edge first = edges.get(runStart);
            int runEnd = runStart + 1;
            while (runEnd < edges.size() && sameRoute(first, edges.get(runEnd))) {
                runEnd++;
            }
            for (int i = runStart; i < runEnd; i++) {
                NetworkGraph.Edge edge = edges.get(i);
                GraphPosition source = positions.get(edge.source());
                GraphPosition target = positions.get(edge.target());
                if (source == null || target == null) {
                    continue;
                }
                float offset = (i - runStart - (runEnd - runStart - 1) / 2.0f) * 5.0f;
                ConnectionBatch batch = connections.get(edge.type());
                int index = batch.add(source, target, offset, NODE_RADIUS);
                ConnectionRef ref = new ConnectionRef(edge.type(), index, edge.source(), edge.target(), offset);
                connectionRefs.computeIfAbsent(edge.source(), key -> new ArrayList<>()).add(ref);
                connectionRefs.computeIfAbsent(edge.target(), key -> new ArrayList<>()).add(ref);
            }
            runStart = runEnd;
        }
    }

    private void drawConnections(GuiGraphics graphics, Theme theme, long elapsedMillis) {
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();
        int casingColor = 0xD0000000 | (theme.borderStrong() & 0x00FFFFFF);
        for (ChannelType type : ChannelType.values()) {
            connections.get(type).drawCasing(consumer, matrix, camera, casingColor);
        }
        for (ChannelType type : ChannelType.values()) {
            connections.get(type).drawSignal(consumer, matrix, camera, color(type));
        }
        for (ChannelType type : ChannelType.values()) {
            connections.get(type).drawPulses(consumer, matrix, camera, color(type), theme.glow(), elapsedMillis,
                    type.ordinal() * 23);
        }
    }

    private boolean sameRoute(NetworkGraph.Edge left, NetworkGraph.Edge right) {
        return left.source().equals(right.source()) && left.target().equals(right.target());
    }

    private void updateConnections(String key) {
        for (ConnectionRef ref : connectionRefs.getOrDefault(key, List.of())) {
            connections.get(ref.type()).update(ref.index(), positions.get(ref.source()), positions.get(ref.target()),
                    ref.offset(), NODE_RADIUS);
        }
    }

    private void drawNodes(GuiGraphics graphics, Font font, String hovered, Theme theme) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            GraphPosition position = positions.get(vertex.key());
            if (position == null || !nodeVisible(position)) {
                continue;
            }
            int centerX = Math.round(position.x());
            int centerY = Math.round(position.y());
            int fill = vertex.key().equals(hovered) ? theme.accentSoft() : theme.surface2();
            GraphSelection.State state = selection.state(memberIds(vertex));
            int border = state == GraphSelection.State.ALL ? theme.accent()
                    : state == GraphSelection.State.PARTIAL ? theme.warn() : theme.borderStrong();
            drawHexagon(graphics, centerX, centerY, fill, border);
        }
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            GraphPosition position = positions.get(vertex.key());
            if (position == null || !nodeVisible(position)) {
                continue;
            }
            int centerX = Math.round(position.x());
            int centerY = Math.round(position.y());
            boolean focused = vertex.key().equals(this.focused) || vertex.key().equals(hovered);
            ItemStack icon = iconFor(vertex, selectedMember);
            if (!icon.isEmpty() && (camera.zoom() >= 0.35f || focused)) {
                graphics.renderItem(icon, centerX - 8, centerY - 8);
            }
            if ((camera.zoom() >= 0.45f || focused) && vertex.key().startsWith("label:")) {
                String label = font.plainSubstrByWidth(vertex.label(), 84);
                int labelX = centerX - font.width(label) / 2;
                graphics.drawString(font, label, labelX, centerY + NODE_HALF_HEIGHT + 4, theme.text(), false);
            }
        }
    }

    static ItemStack iconFor(NetworkGraph.Vertex vertex, UUID selectedMember) {
        GraphNode member = vertex.members().getFirst();
        for (GraphNode candidate : vertex.members()) {
            if (candidate.nodeId().equals(selectedMember)) {
                member = candidate;
                break;
            }
        }
        ResourceLocation blockId = ResourceLocation.parse(member.blockName());
        var item = BuiltInRegistries.BLOCK.get(blockId).asItem();
        return item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    private boolean nodeVisible(GraphPosition position) {
        float radius = (NODE_RADIUS + 8) * camera.zoom();
        float centerX = camera.screenX(position.x());
        float centerY = camera.screenY(position.y());
        return centerX + radius >= camera.x() && centerX - radius < camera.x() + camera.width()
                && centerY + radius >= camera.y() && centerY - radius < camera.y() + camera.height();
    }

    private void drawHexagon(GuiGraphics graphics, int centerX, int centerY, int fill, int border) {
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();
        addHexagon(consumer, matrix, centerX, centerY, NODE_RADIUS, NODE_HALF_HEIGHT, border);
        addHexagon(consumer, matrix, centerX, centerY, NODE_RADIUS - 2, NODE_HALF_HEIGHT - 2, fill);
    }

    private void addHexagon(VertexConsumer consumer, Matrix4f matrix, int centerX, int centerY,
                            int radius, int halfHeight, int color) {
        int shoulder = radius / 2;
        addQuad(consumer, matrix, centerX - shoulder, centerY - halfHeight,
                centerX + shoulder, centerY - halfHeight, centerX + radius, centerY,
                centerX - radius, centerY, color);
        addQuad(consumer, matrix, centerX - radius, centerY, centerX + radius, centerY,
                centerX + shoulder, centerY + halfHeight, centerX - shoulder, centerY + halfHeight, color);
    }

    private void addQuad(VertexConsumer consumer, Matrix4f matrix,
                         float x1, float y1, float x2, float y2,
                         float x3, float y3, float x4, float y4, int color) {
        consumer.addVertex(matrix, x4, y4, 0.0f).setColor(color);
        consumer.addVertex(matrix, x3, y3, 0.0f).setColor(color);
        consumer.addVertex(matrix, x2, y2, 0.0f).setColor(color);
        consumer.addVertex(matrix, x1, y1, 0.0f).setColor(color);
    }

    private String findNode(double mouseX, double mouseY) {
        if (!camera.contains(mouseX, mouseY)) {
            return null;
        }
        float worldX = camera.worldX(mouseX);
        float worldY = camera.worldY(mouseY);
        List<NetworkGraph.Vertex> vertices = graph.vertices();
        for (int i = vertices.size() - 1; i >= 0; i--) {
            NetworkGraph.Vertex vertex = vertices.get(i);
            GraphPosition position = positions.get(vertex.key());
            if (position != null && insideHexagon(worldX - position.x(), worldY - position.y())) {
                return vertex.key();
            }
        }
        return null;
    }

    private boolean insideHexagon(float x, float y) {
        float absoluteX = Math.abs(x);
        float absoluteY = Math.abs(y);
        return absoluteY <= NODE_HALF_HEIGHT
                && absoluteX <= NODE_RADIUS - absoluteY * (NODE_RADIUS / 2.0f) / NODE_HALF_HEIGHT;
    }

    private void clickSelection(String key) {
        if (key == null) {
            if (!controlPressed) selection.replace(List.of());
            focused = null;
            selectedMember = null;
            if (!controlPressed) selectionChanged.accept(null);
            return;
        }
        NetworkGraph.Vertex vertex = vertex(key);
        selection.click(memberIds(vertex), controlPressed);
        focused = key;
        selectedMember = vertex.members().getFirst().nodeId();
        if (!controlPressed) selectionChanged.accept(key);
    }

    private boolean skipSavedPosition(String key) {
        return draggingNode && pressedPositions.containsKey(key);
    }

    private void cancelInteraction() {
        pressedNode = null;
        pressedPositions = Map.of();
        draggingNode = false;
        panning = false;
        boxSelecting = false;
        controlPressed = false;
        interactionMoved = false;
    }

    private Map<String, GraphPosition> dragPositions(String key) {
        Set<String> keys = getSelectedVertices();
        if (!keys.contains(key)) keys = Set.of(key);
        Map<String, GraphPosition> dragged = new LinkedHashMap<>();
        for (String selectedKey : keys) dragged.put(selectedKey, positions.get(selectedKey));
        return dragged;
    }

    private List<UUID> nodesInBox(double startX, double startY, double endX, double endY) {
        double minX = Math.min(startX, endX);
        double maxX = Math.max(startX, endX);
        double minY = Math.min(startY, endY);
        double maxY = Math.max(startY, endY);
        List<UUID> nodes = new ArrayList<>();
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            GraphPosition position = positions.get(vertex.key());
            if (position == null) continue;
            float screenX = camera.screenX(position.x());
            float screenY = camera.screenY(position.y());
            if (screenX >= minX && screenX <= maxX && screenY >= minY && screenY <= maxY) {
                nodes.addAll(memberIds(vertex));
            }
        }
        return nodes;
    }

    private void drawSelectionBox(GuiGraphics graphics, Theme theme) {
        int x = (int) Math.min(pressX, lastMouseX);
        int y = (int) Math.min(pressY, lastMouseY);
        int width = (int) Math.abs(lastMouseX - pressX);
        int height = (int) Math.abs(lastMouseY - pressY);
        graphics.fill(x, y, x + width, y + height, 0x30000000 | (theme.accent() & 0x00FFFFFF));
        graphics.renderOutline(x, y, width, height, theme.accent());
    }

    private List<UUID> memberIds(NetworkGraph.Vertex vertex) {
        return vertex == null ? List.of() : vertex.members().stream().map(GraphNode::nodeId).toList();
    }

    private String keyForMember(UUID memberId) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            if (memberIds(vertex).contains(memberId)) return vertex.key();
        }
        return null;
    }

    private void renderTooltipDetails(GuiGraphics graphics, Font font, String key, int mouseX, int mouseY,
                                      Theme theme) {
        NetworkGraph.Vertex vertex = vertex(key);
        if (vertex == null) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        String title = vertex.label().isEmpty() ? vertex.members().getFirst().blockName() : vertex.label();
        lines.add(Component.literal(title).withColor(rgb(theme.accent())));
        lines.add(Component.translatable("gui.logisticsnetworks.graph.members", vertex.members().size())
                .withColor(rgb(theme.textMuted())));
        int shown = Math.min(vertex.members().size(), MAX_TOOLTIP_MEMBERS);
        Set<GraphChannel> routes = new LinkedHashSet<>();
        for (int i = 0; i < shown; i++) {
            GraphNode member = vertex.members().get(i);
            lines.add(Component.translatable("gui.logisticsnetworks.graph.member_detail",
                    member.blockName(), member.attachedPos().getX(), member.attachedPos().getY(),
                    member.attachedPos().getZ(), member.dimension().toString()).withColor(rgb(theme.text())));
            if (member.dimensional()) {
                lines.add(Component.translatable("gui.logisticsnetworks.graph.dimensional")
                        .withColor(rgb(theme.info())));
            }
            routes.addAll(member.channels());
        }
        for (int i = shown; i < vertex.members().size(); i++) {
            routes.addAll(vertex.members().get(i).channels());
        }
        if (vertex.members().size() > shown) {
            lines.add(Component.translatable("gui.logisticsnetworks.graph.more_members",
                    vertex.members().size() - shown).withColor(rgb(theme.textMuted())));
        }
        if (!routes.isEmpty()) {
            lines.add(Component.translatable("gui.logisticsnetworks.graph.routes", routes.size())
                    .withColor(rgb(theme.textMuted())));
            int routeIndex = 0;
            for (GraphChannel route : routes) {
                if (routeIndex++ >= MAX_TOOLTIP_ROUTES) {
                    break;
                }
                lines.add(Component.translatable("gui.logisticsnetworks.graph.route", route.index() + 1,
                        channelName(route.type()), modeName(route.mode())).withColor(rgb(theme.textSubtle())));
            }
            if (routes.size() > MAX_TOOLTIP_ROUTES) {
                lines.add(Component.translatable("gui.logisticsnetworks.graph.more_routes",
                        routes.size() - MAX_TOOLTIP_ROUTES).withColor(rgb(theme.textMuted())));
            }
        }
        drawTooltip(graphics, font, lines, mouseX, mouseY, theme);
    }

    private void drawTooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY,
                             Theme theme) {
        int contentWidth = 0;
        for (Component line : lines) contentWidth = Math.max(contentWidth, font.width(line));
        int boxWidth = contentWidth + 8;
        int boxHeight = lines.size() * 10 + 6;
        int x = mouseX + 12;
        if (x + boxWidth > camera.x() + camera.width() - 4) x = mouseX - boxWidth - 4;
        x = Math.max(camera.x() + 4, x);
        int y = Math.max(camera.y() + 4, mouseY - 12);
        if (y + boxHeight > camera.y() + camera.height() - 4) {
            y = Math.max(camera.y() + 4, camera.y() + camera.height() - boxHeight - 4);
        }
        ThemePaint.window(graphics, x, y, boxWidth, boxHeight, theme);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + 4, y + 4 + i * 10, theme.text(), false);
        }
    }

    private NetworkGraph.Vertex vertex(String key) {
        for (NetworkGraph.Vertex vertex : graph.vertices()) {
            if (vertex.key().equals(key)) {
                return vertex;
            }
        }
        return null;
    }

    private void fitIfReady() {
        if (fitPending && camera.width() > 0 && camera.height() > 0) {
            fit();
        }
    }

    private static Component channelName(ChannelType type) {
        return Component.translatable("gui.logisticsnetworks.graph.channel."
                + type.name().toLowerCase(Locale.ROOT));
    }

    private static Component modeName(ChannelMode mode) {
        return Component.translatable("gui.logisticsnetworks.graph.mode."
                + mode.name().toLowerCase(Locale.ROOT));
    }

    private static int color(ChannelType type) {
        return switch (type) {
            case ITEM -> 0xFFF2B84B;
            case FLUID -> 0xFF39CFE8;
            case ENERGY -> 0xFFE15B5B;
            case CHEMICAL -> 0xFF52C77A;
            case SOURCE -> 0xFFA56CE3;
        };
    }

    private static int rgb(int color) {
        return color & 0x00FFFFFF;
    }

    private record ConnectionRef(ChannelType type, int index, String source, String target, float offset) {
    }

}
