package me.almana.logisticsnetworks.client.flow;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FlowLayout {
    private FlowLayout() {
    }

    static List<FlowSegment> build(List<FlowAnchor> sources, List<FlowAnchor> targets, int axis, double lane) {
        return build(sources, targets, axis, lane, Collections.nCopies(sources.size(), 0.0),
                Collections.nCopies(targets.size(), 0.0));
    }

    static List<FlowSegment> build(List<FlowAnchor> sources, List<FlowAnchor> targets, int axis, double lane,
                                   List<Double> sourceTimes, List<Double> targetTimes) {
        if (sources.isEmpty() || targets.isEmpty()) return List.of();
        int secondary = axis == 0 ? 2 : 0;
        Vec3 shift = FlowMesh.coordinate(Vec3.ZERO, secondary, lane);
        Vec3 sourceCenter = center(sources).add(shift);
        Vec3 targetCenter = center(targets).add(shift);
        Vec3 sourceHub = hub(sourceCenter, targetCenter, sources.size(), targets.size(), axis);
        Vec3 targetHub = hub(targetCenter, sourceCenter, targets.size(), sources.size(), axis);
        List<Branch> incoming = branches(sources, sourceTimes, sourceHub, axis, secondary, shift, true);
        List<Branch> outgoing = branches(targets, targetTimes, targetHub, axis, secondary, shift, false);
        List<Vec3> trunk = between(sourceHub, targetHub, axis, secondary);
        if (sources.size() == 1) trim(trunk, sources.getFirst(), true);
        if (targets.size() == 1) trim(trunk, targets.getFirst(), false);
        if (trunk.isEmpty()) return direct(sources, targets, sourceTimes, targetTimes, axis, secondary, shift);
        if (sources.size() == 1) sourceHub = trunk.getFirst();
        if (targets.size() == 1) targetHub = trunk.getLast();
        List<FlowSegment> result = new ArrayList<>();
        Arrival arrival = collect(incoming, sourceHub, sourceTimes.getFirst(), result);
        double targetStart = Collections.min(targetTimes);
        FlowMesh middle = new FlowMesh(List.of(trunk), Set.of());
        Map<Vec3, Double> middleDistances = middle.distances(Map.of(sourceHub, 0.0), false);
        double trunkStart = Math.max(arrival.timed(), targetStart + arrival.distance());
        for (FlowMesh.Edge edge : middle.edges()) {
            double distance = middleDistances.get(edge.start());
            result.add(new FlowSegment(edge.start(), edge.end(), distance, trunkStart + distance));
        }
        double trunkLength = middleDistances.getOrDefault(FlowMesh.normalize(targetHub), 0.0);
        distribute(outgoing, targetHub, trunkLength, trunkStart + trunkLength,
                arrival.distance() + trunkLength, result);
        return sorted(result);
    }

    private static Arrival collect(List<Branch> paths, Vec3 hub, double initial, List<FlowSegment> result) {
        if (paths.isEmpty()) return new Arrival(initial, 0);
        FlowMesh mesh = mesh(paths, Set.of(hub));
        Map<Vec3, Double> arrivals = mesh.distances(seeds(paths, true, true), false);
        Map<Vec3, Double> distances = mesh.distances(seeds(paths, true, false), false);
        Map<Vec3, Double> phases = mesh.distances(Map.of(hub, 0.0), true);
        for (FlowMesh.Edge edge : mesh.edges()) {
            result.add(new FlowSegment(edge.start(), edge.end(), -phases.get(edge.start()), arrivals.get(edge.start())));
        }
        Vec3 normalizedHub = FlowMesh.normalize(hub);
        return new Arrival(arrivals.getOrDefault(normalizedHub, initial), distances.getOrDefault(normalizedHub, 0.0));
    }

    private static void distribute(List<Branch> paths, Vec3 hub, double phase, double arrival,
                                    double travelled, List<FlowSegment> result) {
        FlowMesh mesh = mesh(paths, Set.of(hub));
        Map<Vec3, Double> distances = mesh.distances(Map.of(hub, 0.0), false);
        Map<Vec3, Double> activations = mesh.activationTimes(seeds(paths, false, true));
        for (FlowMesh.Edge edge : mesh.edges()) {
            double distance = distances.get(edge.start());
            double reveal = Math.max(arrival, activations.get(edge.end()) + travelled) + distance;
            result.add(new FlowSegment(edge.start(), edge.end(), phase + distance, reveal));
        }
    }

    private static List<FlowSegment> direct(List<FlowAnchor> sources, List<FlowAnchor> targets,
                                            List<Double> sourceTimes, List<Double> targetTimes,
                                            int axis, int secondary, Vec3 shift) {
        List<Branch> paths = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            for (int j = 0; j < targets.size(); j++) {
                List<Vec3> path = between(sources.get(i).position().add(shift), targets.get(j).position().add(shift),
                        axis, secondary);
                trim(path, sources.get(i), true);
                trim(path, targets.get(j), false);
                if (path.size() > 1) paths.add(new Branch(path, Math.max(sourceTimes.get(i), targetTimes.get(j))));
            }
        }
        FlowMesh mesh = mesh(paths, Set.of());
        List<FlowSegment> result = new ArrayList<>();
        boolean outgoing = sources.size() == 1;
        Map<Vec3, Double> phases = mesh.distances(seeds(paths, outgoing, false), !outgoing);
        Map<Vec3, Double> arrivals = mesh.distances(seeds(paths, true, true), false);
        Map<Vec3, Double> activations = mesh.activationTimes(seeds(paths, false, true));
        for (FlowMesh.Edge edge : mesh.edges()) {
            double phase = phases.get(edge.start()) * (outgoing ? 1 : -1);
            double reveal = outgoing ? phase + activations.get(edge.end()) : arrivals.get(edge.start());
            result.add(new FlowSegment(edge.start(), edge.end(), phase, reveal));
        }
        return sorted(result);
    }

    private static List<Branch> branches(List<FlowAnchor> anchors, List<Double> times, Vec3 hub, int axis,
                                         int secondary, Vec3 shift, boolean incoming) {
        if (anchors.size() == 1) return List.of();
        List<Branch> paths = new ArrayList<>();
        int tertiary = 3 - axis - secondary;
        for (int i = 0; i < anchors.size(); i++) {
            FlowAnchor anchor = anchors.get(i);
            Vec3 start = anchor.position().add(shift);
            Vec3 corner = FlowMesh.coordinate(start, axis, FlowMesh.coordinate(hub, axis));
            Vec3 bar = FlowMesh.coordinate(corner, tertiary, FlowMesh.coordinate(hub, tertiary));
            List<Vec3> path = new ArrayList<>(List.of(start, corner, bar, hub));
            trim(path, anchor, true);
            if (!incoming) Collections.reverse(path);
            if (path.size() > 1) paths.add(new Branch(path, times.get(i)));
        }
        return paths;
    }

    private static Map<Vec3, Double> seeds(List<Branch> paths, boolean start, boolean timed) {
        Map<Vec3, Double> result = new HashMap<>();
        for (Branch path : paths) {
            Vec3 point = start ? path.points().getFirst() : path.points().getLast();
            result.merge(point, timed ? path.startedAt() : 0.0, Math::min);
        }
        return result;
    }

    private static FlowMesh mesh(List<Branch> paths, Set<Vec3> junctions) {
        return new FlowMesh(paths.stream().map(Branch::points).toList(), junctions);
    }

    private static List<FlowSegment> sorted(List<FlowSegment> segments) {
        return segments.stream().sorted(java.util.Comparator.comparing(FlowSegment::start, FlowMesh.POINT_ORDER)
                .thenComparing(FlowSegment::end, FlowMesh.POINT_ORDER)).toList();
    }

    private static List<Vec3> between(Vec3 source, Vec3 target, int axis, int secondary) {
        int tertiary = 3 - axis - secondary;
        double middle = (FlowMesh.coordinate(source, axis) + FlowMesh.coordinate(target, axis)) / 2;
        Vec3 first = FlowMesh.coordinate(source, axis, middle);
        Vec3 second = FlowMesh.coordinate(first, tertiary, FlowMesh.coordinate(target, tertiary));
        Vec3 third = FlowMesh.coordinate(second, secondary, FlowMesh.coordinate(target, secondary));
        return new ArrayList<>(List.of(source, first, second, third, target));
    }

    private static void trim(List<Vec3> points, FlowAnchor anchor, boolean start) {
        if (!start) Collections.reverse(points);
        while (points.size() > 1 && anchor.contains(points.get(1))) points.removeFirst();
        if (points.size() < 2) points.clear();
        else if (anchor.contains(points.getFirst())) points.set(0, anchor.boundary(points.getFirst(), points.get(1)));
        if (!start) Collections.reverse(points);
    }

    private static Vec3 hub(Vec3 own, Vec3 other, int ownCount, int otherCount, int axis) {
        if (ownCount == 1) return own;
        double fraction = otherCount == 1 ? 0.5 : 1.0 / 3;
        double value = FlowMesh.coordinate(own, axis) * (1 - fraction) + FlowMesh.coordinate(other, axis) * fraction;
        return FlowMesh.coordinate(otherCount == 1 ? other : own, axis, value);
    }

    static int axis(List<FlowAnchor> sources, List<FlowAnchor> targets) {
        Vec3 difference = center(targets).subtract(center(sources));
        if (difference.lengthSqr() > 1.0E-8) return FlowMesh.axis(difference);
        List<FlowAnchor> all = new ArrayList<>(sources);
        all.addAll(targets);
        Vec3 minimum = all.getFirst().position(), maximum = minimum;
        for (FlowAnchor anchor : all) {
            Vec3 point = anchor.position();
            minimum = new Vec3(Math.min(minimum.x, point.x), Math.min(minimum.y, point.y), Math.min(minimum.z, point.z));
            maximum = new Vec3(Math.max(maximum.x, point.x), Math.max(maximum.y, point.y), Math.max(maximum.z, point.z));
        }
        return FlowMesh.axis(maximum.subtract(minimum));
    }

    private static Vec3 center(List<FlowAnchor> anchors) {
        Vec3 total = Vec3.ZERO;
        for (FlowAnchor anchor : anchors.stream().sorted(java.util.Comparator.comparing(FlowAnchor::position,
                FlowMesh.POINT_ORDER)).toList()) total = total.add(anchor.position());
        return total.scale(1.0 / anchors.size());
    }

    private record Branch(List<Vec3> points, double startedAt) {
    }

    private record Arrival(double timed, double distance) {
    }
}
