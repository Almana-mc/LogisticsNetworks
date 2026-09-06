// Enable when 26.1.2 is supported.
/*
Source owner: LogisticsNodeEntity

public void moveAttachment(BlockPos pos, Vec3 entityPos) {
    BlockPos previousPos = getAttachedPos();
    setPos(entityPos);
    setAttachedPos(pos);

    if (level() instanceof ServerLevel serverLevel && getNetworkId() != null) {
        NetworkRegistry registry = NetworkRegistry.get(serverLevel);
        registry.evictCapabilities(serverLevel, previousPos);
        registry.evictCapabilities(serverLevel, pos);
        registry.invalidateNetwork(getNetworkId());
    }
}
*/
