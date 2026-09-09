package me.almana.logisticsnetworks.client.flow;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

final class FlowMesh {
    private static final double POINT_PRECISION = 1_000_000;
    static final Comparator<Vec3> POINT_ORDER = Comparator.comparingDouble((Vec3 point) -> point.x)
            .thenComparingDouble(point -> point.y).thenComparingDouble(point -> point.z);
    private final List<Edge> edges;

    FlowMesh(List<List<Vec3>> paths, Set<Vec3> junctions) {
        List<List<Vec3>> normalizedPaths = paths.stream()
                .map(path -> path.stream().map(FlowMesh::normalize).toList()).toList();
        Set<Vec3> normalizedJunctions = junctions.stream().map(FlowMesh::normalize)
                .collect(java.util.stream.Collectors.toSet());
        edges = roundCorners(merge(normalizedPaths), normalizedJunctions).stream()
                .filter(edge -> edge.length() > 1.0E-8)
                .sorted(Comparator.comparing(Edge::start, POINT_ORDER).thenComparing(Edge::end, POINT_ORDER))
                .toList();
    }

    List<Edge> edges() {
        return edges;
    }

    Map<Vec3, Double> distances(Map<Vec3, Double> seeds, boolean reverse) {
        return propagate(seeds, reverse, true);
    }

    Map<Vec3, Double> activationTimes(Map<Vec3, Double> seeds) {
        return propagate(seeds, true, false);
    }

    private Map<Vec3, Double> propagate(Map<Vec3, Double> seeds, boolean reverse, boolean travel) {
        Map<Vec3, List<Edge>> adjacency = adjacency(edges, reverse);
        Map<Vec3, Double> distances = new HashMap<>();
        seeds.forEach((point, distance) -> distances.merge(normalize(point), distance, Math::min));
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::distance));
        distances.forEach((point, distance) -> queue.add(new Visit(point, distance)));
        while (!queue.isEmpty()) {
            Visit visit = queue.remove();
            if (visit.distance() > distances.get(visit.point())) continue;
            for (Edge edge : adjacency.getOrDefault(visit.point(), List.of())) {
                Vec3 next = reverse ? edge.start() : edge.end();
                double distance = visit.distance() + (travel ? edge.length() : 0);
                if (distance < distances.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    distances.put(next, distance);
                    queue.add(new Visit(next, distance));
                }
            }
        }
        return distances;
    }

    private static List<Edge> merge(List<List<Vec3>> paths) {
        Map<Line, TreeMap<Double, Integer>> lines = new LinkedHashMap<>();
        for (List<Vec3> path : paths) {
            for (int i = 1; i < path.size(); i++) {
                Vec3 start = path.get(i - 1);
                Vec3 end = path.get(i);
                if (start.distanceToSqr(end) < 1.0E-16) continue;
                int axis = axis(end.subtract(start));
                double from = coordinate(start, axis);
                double to = coordinate(end, axis);
                Line line = new Line(axis, coordinate(start, (axis + 1) % 3),
                        coordinate(start, (axis + 2) % 3), to > from);
                TreeMap<Double, Integer> events = lines.computeIfAbsent(line, ignored -> new TreeMap<>());
                events.merge(Math.min(from, to), 1, Integer::sum);
                events.merge(Math.max(from, to), -1, Integer::sum);
            }
        }
        List<Edge> result = new ArrayList<>();
        lines.forEach((line, events) -> splitLine(line, events, result));
        return result;
    }

    private static void splitLine(Line line, TreeMap<Double, Integer> events, List<Edge> result) {
        int count = 0;
        double previous = events.firstKey();
        for (var event : events.entrySet()) {
            if (count > 0 && event.getKey() > previous) {
                Vec3 start = line.point(previous);
                Vec3 end = line.point(event.getKey());
                result.add(line.forward() ? new Edge(start, end) : new Edge(end, start));
            }
            count += event.getValue();
            previous = event.getKey();
        }
    }

    private static List<Edge> roundCorners(List<Edge> edges, Set<Vec3> junctions) {
        Map<Vec3, List<Edge>> incoming = adjacency(edges, true);
        Map<Vec3, List<Edge>> outgoing = adjacency(edges, false);
        Map<Vec3, Turn> turns = new HashMap<>();
        for (var entry : incoming.entrySet()) {
            List<Edge> next = outgoing.getOrDefault(entry.getKey(), List.of());
            if (entry.getValue().size() != 1 || next.size() != 1 || junctions.contains(entry.getKey())) continue;
            Edge before = entry.getValue().getFirst();
            Edge after = next.getFirst();
            Vec3 a = before.end().subtract(before.start()).normalize();
            Vec3 b = after.end().subtract(after.start()).normalize();
            if (Math.abs(a.dot(b)) > 0.01) continue;
            double radius = Math.min(0.125, Math.min(before.length(), after.length()) * 0.25);
            turns.put(entry.getKey(), new Turn(entry.getKey().subtract(a.scale(radius)),
                    entry.getKey(), entry.getKey().add(b.scale(radius))));
        }
        List<Edge> rounded = new ArrayList<>();
        for (Edge edge : edges) {
            Turn start = turns.get(edge.start());
            Turn end = turns.get(edge.end());
            rounded.add(new Edge(start == null ? edge.start() : start.end(), end == null ? edge.end() : end.start()));
        }
        turns.values().forEach(turn -> turn.append(rounded));
        return rounded;
    }

    private static Map<Vec3, List<Edge>> adjacency(List<Edge> edges, boolean reverse) {
        Map<Vec3, List<Edge>> result = new HashMap<>();
        for (Edge edge : edges) {
            result.computeIfAbsent(reverse ? edge.end() : edge.start(), ignored -> new ArrayList<>()).add(edge);
        }
        return result;
    }

    static int axis(Vec3 vector) {
        double x = Math.abs(vector.x), y = Math.abs(vector.y), z = Math.abs(vector.z);
        return x >= z && x >= y ? 0 : z >= y ? 2 : 1;
    }

    static double coordinate(Vec3 point, int axis) {
        return axis == 0 ? point.x : axis == 1 ? point.y : point.z;
    }

    static Vec3 coordinate(Vec3 point, int axis, double value) {
        return new Vec3(axis == 0 ? value : point.x, axis == 1 ? value : point.y, axis == 2 ? value : point.z);
    }

    static Vec3 normalize(Vec3 point) {
        return new Vec3(normalize(point.x), normalize(point.y), normalize(point.z));
    }

    private static double normalize(double coordinate) {
        return Math.rint(coordinate * POINT_PRECISION) / POINT_PRECISION;
    }

    record Edge(Vec3 start, Vec3 end) {
        double length() {
            return start.distanceTo(end);
        }
    }

    private record Visit(Vec3 point, double distance) {
    }

    private record Line(int axis, double second, double third, boolean forward) {
        Vec3 point(double value) {
            return coordinate(coordinate(coordinate(Vec3.ZERO, axis, value), (axis + 1) % 3, second),
                    (axis + 2) % 3, third);
        }
    }

    private record Turn(Vec3 start, Vec3 corner, Vec3 end) {
        void append(List<Edge> edges) {
            Vec3 previous = start;
            for (int step = 1; step <= 4; step++) {
                double t = step / 4.0;
                Vec3 next = start.scale((1 - t) * (1 - t)).add(corner.scale(2 * t * (1 - t))).add(end.scale(t * t));
                edges.add(new Edge(previous, next));
                previous = next;
            }
        }
    }
}
