package me.almana.logisticsnetworks.client.graph;

import me.almana.logisticsnetworks.data.graph.GraphPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphLayout {
    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    private GraphLayout() {
    }

    public static Map<String, GraphPosition> distribute(Map<String, GraphPosition> positions,
                                                         Collection<String> selected, Axis axis) {
        List<String> keys = new ArrayList<>();
        for (String key : selected) {
            if (positions.containsKey(key)) keys.add(key);
        }
        if (keys.size() < 3) return Map.of();

        keys.sort(Comparator.comparingDouble(key -> coordinate(positions.get(key), axis)));
        float first = coordinate(positions.get(keys.getFirst()), axis);
        float step = (coordinate(positions.get(keys.getLast()), axis) - first) / (keys.size() - 1);
        Map<String, GraphPosition> moved = new LinkedHashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            String key = keys.get(index);
            GraphPosition current = positions.get(key);
            float coordinate = first + step * index;
            moved.put(key, axis == Axis.HORIZONTAL
                    ? new GraphPosition(coordinate, current.y())
                    : new GraphPosition(current.x(), coordinate));
        }
        return moved;
    }

    private static float coordinate(GraphPosition position, Axis axis) {
        return axis == Axis.HORIZONTAL ? position.x() : position.y();
    }
}
