// Enable when 26.1.2 is supported.
/*
Source owner: Snapshots

if (!TransferEngine.canRunChannel(node, channel) || !CreateCompat.isResolved(node)) {
    continue;
}
if (!node.isMountedOnCreate() && !level.isLoaded(node.getAttachedPos())) {
    continue;
}
IItemHandler sourceHandler = capCache.findItemHandler(node, channel.getIoDirection());

Source owner: TransferCommitter

if (!sourceNode.isMountedOnCreate() && !node.isMountedOnCreate()
        && TransferEngine.isSameItemStorage(
                (ServerLevel) sourceNode.level(), sourceNode.getAttachedPos(),
                (ServerLevel) node.level(), node.getAttachedPos())) {
    continue;
}

private static boolean isEndpointLoaded(LogisticsNodeEntity node) {
    if (node.isMountedOnCreate()) {
        return CreateCompat.isResolved(node);
    }
    return node.level() instanceof ServerLevel level && level.isLoaded(node.getAttachedPos());
}

Source owner: ItemEndpointTable

EndpointKey key = EndpointKey.of(node, direction);
Integer existing = indexes.get(key);
if (existing == null && !node.isMountedOnCreate()) {
    Map<Integer, Integer> sides = handlerIndexes.get(handler);
    existing = sides != null ? sides.get(key.direction()) : null;
}

if (!node.isMountedOnCreate()) {
    handlerIndexes.computeIfAbsent(handler, ignored -> new HashMap<>())
            .put(key.direction(), index);
}

private record EndpointKey(
        @Nullable ResourceKey<Level> dimension,
        long position,
        @Nullable UUID mountedNode,
        int direction) {

    private static EndpointKey of(LogisticsNodeEntity node, @Nullable Direction direction) {
        int side = direction == null ? ALL_SIDES : direction.ordinal();
        if (node.isMountedOnCreate()) {
            return new EndpointKey(null, 0L, node.getUUID(), side);
        }
        ServerLevel level = (ServerLevel) node.level();
        return new EndpointKey(level.dimension(), node.getAttachedPos().asLong(), null, side);
    }
}
*/
