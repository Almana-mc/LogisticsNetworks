package me.almana.logisticsnetworks.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NodeGeometry {

    private static final float DEFAULT_MIN_X = -0.5f;
    private static final float DEFAULT_MIN_Y = 0.0f;
    private static final float DEFAULT_MIN_Z = -0.5f;
    private static final float DEFAULT_MAX_X = 0.5f;
    private static final float DEFAULT_MAX_Y = 1.0f;
    private static final float DEFAULT_MAX_Z = 0.5f;
    private static final float FRAME = 2.0f / 16.0f;
    private static final float EPS = 0.001f;
    private static final float UV_MIN = 0.0f;
    private static final float UV_MAX = 16.0f / 64.0f;

    private static final int WEST = 1;
    private static final int EAST = 1 << 1;
    private static final int DOWN = 1 << 2;
    private static final int UP = 1 << 3;
    private static final int NORTH = 1 << 4;
    private static final int SOUTH = 1 << 5;
    private static final int ALL = WEST | EAST | DOWN | UP | NORTH | SOUTH;

    private static final Map<GeometryKey, Box[]> BOXES = new HashMap<>();

    private NodeGeometry() {
    }

    public static void emit(Matrix4f matrix, VertexConsumer buffer, int connections, int packedLight, int color) {
        for (Box box : boxesFor(connections)) {
            box.emit(matrix, buffer, packedLight, color);
        }
    }

    public static void emit(Matrix4f matrix, VertexConsumer buffer, int connections, int packedLight, int color,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (Box box : boxesFor(connections, minX, minY, minZ, maxX, maxY, maxZ)) {
            box.emit(matrix, buffer, packedLight, color);
        }
    }

    public static void emitBridges(Matrix4f matrix, VertexConsumer buffer, int connections, int packedLight, int color,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (Direction direction : Direction.values()) {
            if (!NodeConnectionMask.has(connections, direction)) {
                continue;
            }
            for (Box box : bridgeBoxesFor(direction, minX, minY, minZ, maxX, maxY, maxZ)) {
                box.emit(matrix, buffer, packedLight, color);
            }
        }
    }

    static Box[] boxesFor(int connections) {
        return boxesFor(connections, DEFAULT_MIN_X, DEFAULT_MIN_Y, DEFAULT_MIN_Z, DEFAULT_MAX_X, DEFAULT_MAX_Y,
                DEFAULT_MAX_Z);
    }

    static Box[] boxesFor(int connections, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        int mask = connections;
        GeometryKey key = new GeometryKey(mask, minX, minY, minZ, maxX, maxY, maxZ);
        return BOXES.computeIfAbsent(key, unused -> create(mask, minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static Box[] bridgeBoxesFor(Direction direction, float minX, float minY, float minZ, float maxX,
            float maxY, float maxZ) {
        return switch (direction) {
            case WEST -> minX > DEFAULT_MIN_X
                    ? boxesFor(axisMask(Direction.WEST), DEFAULT_MIN_X, minY, minZ, minX + EPS, maxY, maxZ)
                    : new Box[0];
            case EAST -> maxX < DEFAULT_MAX_X
                    ? boxesFor(axisMask(Direction.EAST), maxX - EPS, minY, minZ, DEFAULT_MAX_X, maxY, maxZ)
                    : new Box[0];
            case DOWN -> minY > DEFAULT_MIN_Y
                    ? boxesFor(axisMask(Direction.DOWN), minX, DEFAULT_MIN_Y, minZ, maxX, minY + EPS, maxZ)
                    : new Box[0];
            case UP -> maxY < DEFAULT_MAX_Y
                    ? boxesFor(axisMask(Direction.UP), minX, maxY - EPS, minZ, maxX, DEFAULT_MAX_Y, maxZ)
                    : new Box[0];
            case NORTH -> minZ > DEFAULT_MIN_Z
                    ? boxesFor(axisMask(Direction.NORTH), minX, minY, DEFAULT_MIN_Z, maxX, maxY, minZ + EPS)
                    : new Box[0];
            case SOUTH -> maxZ < DEFAULT_MAX_Z
                    ? boxesFor(axisMask(Direction.SOUTH), minX, minY, maxZ - EPS, maxX, maxY, DEFAULT_MAX_Z)
                    : new Box[0];
        };
    }

    private static int axisMask(Direction direction) {
        int mask = NodeConnectionMask.NONE;
        for (Direction other : Direction.values()) {
            if (other.getAxis() == direction.getAxis()) {
                mask = NodeConnectionMask.add(mask, other);
            }
        }
        return mask;
    }

    private static Box[] create(int connections, float minX, float minY, float minZ, float maxX, float maxY,
            float maxZ) {
        boolean north = connected(connections, Direction.NORTH);
        boolean south = connected(connections, Direction.SOUTH);
        boolean west = connected(connections, Direction.WEST);
        boolean east = connected(connections, Direction.EAST);
        boolean up = connected(connections, Direction.UP);
        boolean down = connected(connections, Direction.DOWN);

        boolean northOpen = !north;
        boolean southOpen = !south;
        boolean westOpen = !west;
        boolean eastOpen = !east;

        float frameX = frameSize(minX, maxX);
        float frameY = frameSize(minY, maxY);
        float frameZ = frameSize(minZ, maxZ);

        float northZ0 = minZ - EPS;
        float northZ1 = minZ + frameZ;
        float southZ0 = maxZ - frameZ;
        float southZ1 = maxZ + EPS;
        float westX0 = minX - EPS;
        float westX1 = minX + frameX;
        float eastX0 = maxX - frameX;
        float eastX1 = maxX + EPS;
        float lowY0 = down ? minY : minY - EPS;
        float lowY1 = minY + frameY;
        float highY0 = maxY - frameY;
        float highY1 = up ? maxY : maxY + EPS;
        float postY0 = down ? minY : minY + frameY;
        float postY1 = up ? maxY : maxY - frameY;
        float spanX0 = west ? minX : westX0;
        float spanX1 = east ? maxX : eastX1;
        float spanY0 = down ? minY : lowY0;
        float spanY1 = up ? maxY : highY1;
        float spanZ0 = north ? minZ : northZ0;
        float spanZ1 = south ? maxZ : southZ1;

        List<Box> boxes = new ArrayList<>(12);

        if (northOpen) {
            if (!down) {
                addXEdge(boxes, westOpen ? minX + frameX : minX, eastOpen ? maxX - frameX : maxX, lowY0, lowY1,
                        northZ0, northZ1, SOUTH | UP, down, up);
            }
            if (!up) {
                addXEdge(boxes, westOpen ? minX + frameX : minX, eastOpen ? maxX - frameX : maxX, highY0, highY1,
                        northZ0, northZ1, SOUTH | DOWN, down, up);
            }
        }
        if (southOpen) {
            if (!down) {
                addXEdge(boxes, westOpen ? minX + frameX : minX, eastOpen ? maxX - frameX : maxX, lowY0, lowY1,
                        southZ0, southZ1, NORTH | UP, down, up);
            }
            if (!up) {
                addXEdge(boxes, westOpen ? minX + frameX : minX, eastOpen ? maxX - frameX : maxX, highY0, highY1,
                        southZ0, southZ1, NORTH | DOWN, down, up);
            }
        }
        if (westOpen) {
            if (!down) {
                addZEdge(boxes, westX0, westX1, lowY0, lowY1, northOpen ? minZ + frameZ : minZ,
                        southOpen ? maxZ - frameZ : maxZ, EAST | UP, down, up);
            }
            if (!up) {
                addZEdge(boxes, westX0, westX1, highY0, highY1, northOpen ? minZ + frameZ : minZ,
                        southOpen ? maxZ - frameZ : maxZ, EAST | DOWN, down, up);
            }
        }
        if (eastOpen) {
            if (!down) {
                addZEdge(boxes, eastX0, eastX1, lowY0, lowY1, northOpen ? minZ + frameZ : minZ,
                        southOpen ? maxZ - frameZ : maxZ, WEST | UP, down, up);
            }
            if (!up) {
                addZEdge(boxes, eastX0, eastX1, highY0, highY1, northOpen ? minZ + frameZ : minZ,
                        southOpen ? maxZ - frameZ : maxZ, WEST | DOWN, down, up);
            }
        }

        if (postY1 > postY0) {
            int postFaces = ALL & ~UP & ~DOWN;
            if (up) {
                postFaces &= ~UP;
            }
            if (down) {
                postFaces &= ~DOWN;
            }
            if (northOpen && westOpen) {
                boxes.add(new Box(westX0, postY0, northZ0, westX1, postY1, northZ1, postFaces & ~EAST & ~SOUTH));
            }
            if (northOpen && eastOpen) {
                boxes.add(new Box(eastX0, postY0, northZ0, eastX1, postY1, northZ1, postFaces & ~WEST & ~SOUTH));
            }
            if (southOpen && westOpen) {
                boxes.add(new Box(westX0, postY0, southZ0, westX1, postY1, southZ1, postFaces & ~EAST & ~NORTH));
            }
            if (southOpen && eastOpen) {
                boxes.add(new Box(eastX0, postY0, southZ0, eastX1, postY1, southZ1, postFaces & ~WEST & ~NORTH));
            }
        }

        if (!down) {
            int bottomCapFaces = ALL & ~UP;
            if (northOpen && westOpen) boxes.add(new Box(westX0, lowY0, northZ0, westX1, lowY1, northZ1, bottomCapFaces & ~EAST & ~SOUTH));
            if (northOpen && eastOpen) boxes.add(new Box(eastX0, lowY0, northZ0, eastX1, lowY1, northZ1, bottomCapFaces & ~WEST & ~SOUTH));
            if (southOpen && westOpen) boxes.add(new Box(westX0, lowY0, southZ0, westX1, lowY1, southZ1, bottomCapFaces & ~EAST & ~NORTH));
            if (southOpen && eastOpen) boxes.add(new Box(eastX0, lowY0, southZ0, eastX1, lowY1, southZ1, bottomCapFaces & ~WEST & ~NORTH));
        }
        if (!up) {
            int topCapFaces = ALL & ~DOWN;
            if (northOpen && westOpen) boxes.add(new Box(westX0, highY0, northZ0, westX1, highY1, northZ1, topCapFaces & ~EAST & ~SOUTH));
            if (northOpen && eastOpen) boxes.add(new Box(eastX0, highY0, northZ0, eastX1, highY1, northZ1, topCapFaces & ~WEST & ~SOUTH));
            if (southOpen && westOpen) boxes.add(new Box(westX0, highY0, southZ0, westX1, highY1, southZ1, topCapFaces & ~EAST & ~NORTH));
            if (southOpen && eastOpen) boxes.add(new Box(eastX0, highY0, southZ0, eastX1, highY1, southZ1, topCapFaces & ~WEST & ~NORTH));
        }

        addCornerFillers(boxes, connections, westX0, westX1, eastX0, eastX1, lowY0, lowY1, highY0, highY1,
                northZ0, northZ1, southZ0, southZ1, spanX0, spanX1, spanY0, spanY1, spanZ0, spanZ1);

        return boxes.toArray(Box[]::new);
    }

    private static float frameSize(float min, float max) {
        return Math.min(FRAME, (max - min) * 0.5f);
    }

    private static boolean connected(int mask, Direction direction) {
        return NodeConnectionMask.has(mask, direction);
    }

    private static void addCornerFillers(List<Box> boxes, int connections, float westX0, float westX1,
            float eastX0, float eastX1, float lowY0, float lowY1, float highY0, float highY1, float northZ0,
            float northZ1, float southZ0, float southZ1, float spanX0, float spanX1, float spanY0, float spanY1,
            float spanZ0, float spanZ1) {
        for (Direction first : Direction.values()) {
            for (Direction second : Direction.values()) {
                if (first.ordinal() >= second.ordinal() || !NodeConnectionMask.isCornerPair(first, second)) {
                    continue;
                }
                if (!NodeConnectionMask.hasCorner(connections, first, second)) {
                    continue;
                }
                if (!connected(connections, first) || !connected(connections, second)) {
                    continue;
                }

                float boxMinX = minForAxis(first, second, Direction.WEST, westX0, Direction.EAST, eastX0, spanX0);
                float boxMaxX = maxForAxis(first, second, Direction.WEST, westX1, Direction.EAST, eastX1, spanX1);
                float boxMinY = minForAxis(first, second, Direction.DOWN, lowY0, Direction.UP, highY0, spanY0);
                float boxMaxY = maxForAxis(first, second, Direction.DOWN, lowY1, Direction.UP, highY1, spanY1);
                float boxMinZ = minForAxis(first, second, Direction.NORTH, northZ0, Direction.SOUTH, southZ0, spanZ0);
                float boxMaxZ = maxForAxis(first, second, Direction.NORTH, northZ1, Direction.SOUTH, southZ1, spanZ1);
                int faces = remainingAxisFaces(first, second);

                for (Direction direction : Direction.values()) {
                    if (connected(connections, direction)) {
                        faces &= ~face(direction);
                    }
                }
                if (faces != 0) {
                    boxes.add(new Box(boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ, faces));
                }
            }
        }
    }

    private static float minForAxis(Direction first, Direction second, Direction lowDirection, float lowMin,
            Direction highDirection, float highMin, float spanMin) {
        if (first == lowDirection || second == lowDirection) {
            return lowMin;
        }
        if (first == highDirection || second == highDirection) {
            return highMin;
        }
        return spanMin;
    }

    private static float maxForAxis(Direction first, Direction second, Direction lowDirection, float lowMax,
            Direction highDirection, float highMax, float spanMax) {
        if (first == lowDirection || second == lowDirection) {
            return lowMax;
        }
        if (first == highDirection || second == highDirection) {
            return highMax;
        }
        return spanMax;
    }

    private static int remainingAxisFaces(Direction first, Direction second) {
        if (first.getAxis() != Direction.Axis.X && second.getAxis() != Direction.Axis.X) {
            return WEST | EAST;
        }
        if (first.getAxis() != Direction.Axis.Y && second.getAxis() != Direction.Axis.Y) {
            return DOWN | UP;
        }
        return NORTH | SOUTH;
    }

    private static int face(Direction direction) {
        return switch (direction) {
            case WEST -> NodeGeometry.WEST;
            case EAST -> NodeGeometry.EAST;
            case DOWN -> NodeGeometry.DOWN;
            case UP -> NodeGeometry.UP;
            case NORTH -> NodeGeometry.NORTH;
            case SOUTH -> NodeGeometry.SOUTH;
        };
    }

    private static void addXEdge(List<Box> boxes, float minX, float maxX, float minY, float maxY, float minZ,
            float maxZ, int hiddenFaces, boolean down, boolean up) {
        if (maxX <= minX) {
            return;
        }
        int faces = ALL & ~WEST & ~EAST & ~hiddenFaces;
        if (down) {
            faces &= ~DOWN;
        }
        if (up) {
            faces &= ~UP;
        }
        boxes.add(new Box(minX, minY, minZ, maxX, maxY, maxZ, faces));
    }

    private static void addZEdge(List<Box> boxes, float minX, float maxX, float minY, float maxY, float minZ,
            float maxZ, int hiddenFaces, boolean down, boolean up) {
        if (maxZ <= minZ) {
            return;
        }
        int faces = ALL & ~NORTH & ~SOUTH & ~hiddenFaces;
        if (down) {
            faces &= ~DOWN;
        }
        if (up) {
            faces &= ~UP;
        }
        boxes.add(new Box(minX, minY, minZ, maxX, maxY, maxZ, faces));
    }

    static final class Box {
        final float minX;
        final float minY;
        final float minZ;
        final float maxX;
        final float maxY;
        final float maxZ;
        final int faces;

        Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int faces) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.faces = faces;
        }

        void emit(Matrix4f matrix, VertexConsumer buffer, int packedLight, int color) {
            if ((faces & WEST) != 0) {
                quad(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ,
                        color, packedLight, -1.0f, 0.0f, 0.0f);
            }
            if ((faces & EAST) != 0) {
                quad(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ,
                        color, packedLight, 1.0f, 0.0f, 0.0f);
            }
            if ((faces & DOWN) != 0) {
                quad(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                        color, packedLight, 0.0f, -1.0f, 0.0f);
            }
            if ((faces & UP) != 0) {
                quad(buffer, matrix, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ,
                        color, packedLight, 0.0f, 1.0f, 0.0f);
            }
            if ((faces & NORTH) != 0) {
                quad(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ,
                        color, packedLight, 0.0f, 0.0f, -1.0f);
            }
            if ((faces & SOUTH) != 0) {
                quad(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ,
                        color, packedLight, 0.0f, 0.0f, 1.0f);
            }
        }

        private static void quad(VertexConsumer buffer, Matrix4f matrix, float x0, float y0, float z0, float x1,
                float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int color,
                int packedLight, float normalX, float normalY, float normalZ) {
            vertex(buffer, matrix, x0, y0, z0, UV_MIN, UV_MAX, color, packedLight, normalX, normalY, normalZ);
            vertex(buffer, matrix, x1, y1, z1, UV_MIN, UV_MIN, color, packedLight, normalX, normalY, normalZ);
            vertex(buffer, matrix, x2, y2, z2, UV_MAX, UV_MIN, color, packedLight, normalX, normalY, normalZ);
            vertex(buffer, matrix, x3, y3, z3, UV_MAX, UV_MAX, color, packedLight, normalX, normalY, normalZ);
        }

        private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float u, float v,
                int color, int packedLight, float normalX, float normalY, float normalZ) {
            buffer.addVertex(matrix, x, y, z)
                    .setColor(color)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(normalX, normalY, normalZ);
        }
    }

    private record GeometryKey(int connections, float minX, float minY, float minZ, float maxX, float maxY,
            float maxZ) {
    }
}
