// Enable when 26.1.2 is supported.
/*
Source owner: LogisticsNodeEntity

private static final EntityDataAccessor<Optional<UUID>> CREATE_CONTRAPTION_ID = SynchedEntityData
        .defineId(LogisticsNodeEntity.class, EntityDataSerializers.OPTIONAL_UUID);
private static final EntityDataAccessor<BlockPos> CREATE_LOCAL_POS = SynchedEntityData
        .defineId(LogisticsNodeEntity.class, EntityDataSerializers.BLOCK_POS);

protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(CREATE_CONTRAPTION_ID, Optional.empty());
    builder.define(CREATE_LOCAL_POS, BlockPos.ZERO);
}

NodeState captureState() {
    return new NodeState(getAttachedPos(), isValid(), Optional.ofNullable(getNetworkId()), getNetworkName(),
            getNetworkColor(), isRenderVisible(), Optional.ofNullable(getOwnerUUID()), getNodeLabel(),
            labelRevision, isHighlighted(), Optional.ofNullable(getCreateContraptionId()), getCreateLocalPos(),
            List.of(channels), SlotStack.nonEmpty(Arrays.asList(upgradeItems)));
}

void applyState(NodeState state) {
    entityData.set(CREATE_CONTRAPTION_ID, state.createContraptionId());
    entityData.set(CREATE_LOCAL_POS, state.createLocalPos());
}

public void tick() {
    if (isMountedOnCreate()) {
        CreateCompat.tickMountedNode(this);
        return;
    }
}

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

public boolean isMountedOnCreate() {
    return entityData.get(CREATE_CONTRAPTION_ID).isPresent();
}

@Nullable
public UUID getCreateContraptionId() {
    return entityData.get(CREATE_CONTRAPTION_ID).orElse(null);
}

public BlockPos getCreateLocalPos() {
    return entityData.get(CREATE_LOCAL_POS);
}

public void mountOnCreate(UUID contraptionId, BlockPos localPos) {
    entityData.set(CREATE_CONTRAPTION_ID, Optional.of(contraptionId));
    entityData.set(CREATE_LOCAL_POS, localPos.immutable());
    if (level() instanceof ServerLevel serverLevel && getNetworkId() != null) {
        NetworkRegistry registry = NetworkRegistry.get(serverLevel);
        registry.evictCapabilities(serverLevel, getAttachedPos());
        registry.invalidateNetwork(getNetworkId());
    }
}

public void updateMountedPosition(Vec3 position) {
    setPos(position);
    setAttachedPos(BlockPos.containing(position));
}

public void dismountFromCreate(BlockPos attachedPos, Vec3 position,
        UnaryOperator<Direction> directionTransform) {
    entityData.set(CREATE_CONTRAPTION_ID, Optional.empty());
    entityData.set(CREATE_LOCAL_POS, BlockPos.ZERO);
    for (ChannelData channel : channels) {
        Direction direction = channel.getIoDirection();
        if (direction != null) {
            channel.setIoDirection(directionTransform.apply(direction));
        }
    }
    moveAttachment(attachedPos, position);
}
*/
