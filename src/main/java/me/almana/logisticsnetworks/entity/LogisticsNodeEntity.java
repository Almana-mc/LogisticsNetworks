package me.almana.logisticsnetworks.entity;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.upgrade.NodeUpgradeData;
import me.almana.logisticsnetworks.data.NodeRouteChannels;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.data.SlotStack;
import me.almana.logisticsnetworks.logic.NodeAccessPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import me.almana.logisticsnetworks.logic.TransferCapabilityCache;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LogisticsNodeEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int UPGRADE_SLOT_COUNT = 4;
    public static final int CHANNEL_COUNT = 9;

    private static final EntityDataAccessor<BlockPos> ATTACHED_POS = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> VALID = SynchedEntityData.defineId(LogisticsNodeEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> NETWORK_ID = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NETWORK_NAME = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> RENDER_VISIBLE = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> OWNER_UUID = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NODE_LABEL = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> HIGHLIGHTED = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> NETWORK_COLOR = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Long> ROUTE_CHANNELS = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.LONG);

    private final ChannelData[] channels = new ChannelData[CHANNEL_COUNT];
    private final ItemStack[] upgradeItems = new ItemStack[UPGRADE_SLOT_COUNT];
    private long labelRevision;

    private final long[] channelCooldowns = new long[CHANNEL_COUNT];
    private final float[] backoffTicks = new float[CHANNEL_COUNT];

    private TransferCapabilityCache capabilityCache;

    public LogisticsNodeEntity(EntityType<LogisticsNodeEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
        noPhysics = true;

        for (int i = 0; i < CHANNEL_COUNT; i++) {
            channels[i] = new ChannelData();
        }

        Arrays.fill(upgradeItems, ItemStack.EMPTY);
    }

    public LogisticsNodeEntity(EntityType<LogisticsNodeEntity> entityType, Level level, BlockPos pos) {
        this(entityType, level);
        setPos(Vec3.atCenterOf(pos));
        setAttachedPos(pos);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ATTACHED_POS, BlockPos.ZERO);
        builder.define(VALID, false);
        builder.define(NETWORK_ID, "");
        builder.define(NETWORK_NAME, "");
        builder.define(RENDER_VISIBLE, true);
        builder.define(OWNER_UUID, "");
        builder.define(NODE_LABEL, "");
        builder.define(HIGHLIGHTED, false);
        builder.define(ROUTE_CHANNELS, 0L);
        builder.define(NETWORK_COLOR, me.almana.logisticsnetworks.data.NetworkColors.DEFAULT);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.read(NodeState.MAP_CODEC).ifPresent(this::applyState);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(NodeState.MAP_CODEC, captureState());
    }

    NodeState captureState() {
        return new NodeState(getAttachedPos(), isValid(), Optional.ofNullable(getNetworkId()), getNetworkName(),
                getNetworkColor(), isRenderVisible(), Optional.ofNullable(getOwnerUUID()), getNodeLabel(),
                labelRevision, isHighlighted(), Optional.empty(), BlockPos.ZERO,
                List.of(channels), SlotStack.nonEmpty(Arrays.asList(upgradeItems)));
    }

    void applyState(NodeState state) {
        setAttachedPos(state.attachedPos());
        setValid(state.valid());
        state.networkId().ifPresent(this::setNetworkId);
        setNetworkName(state.networkName());
        setNetworkColor(state.networkColor());
        setRenderVisible(state.renderVisible());
        state.owner().ifPresent(this::setOwnerUUID);
        // Label resets revision; keep order
        setNodeLabel(state.nodeLabel());
        setLabelRevision(state.labelRevision());
        setHighlighted(state.highlighted());
        for (int i = 0; i < Math.min(CHANNEL_COUNT, state.channels().size()); i++) {
            channels[i].copyFrom(state.channels().get(i));
        }
        System.arraycopy(SlotStack.toSlots(state.upgrades(), UPGRADE_SLOT_COUNT), 0, upgradeItems, 0,
                UPGRADE_SLOT_COUNT);
        refreshRouteChannels();
    }

    public CompoundTag saveNodeState() {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess());
        addAdditionalSaveData(output);
        return output.buildResult();
    }

    public void loadNodeState(CompoundTag tag) {
        readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), tag));
    }

    @Override
    public void tick() {
        if (this.level().isClientSide()) return;
        if (this.tickCount <= 1 || this.tickCount % 5 == 0) refreshRouteChannels();

        BlockPos attached = getAttachedPos();
        if (!attached.equals(BlockPos.ZERO)) {
            Vec3 target = Vec3.atBottomCenterOf(attached);
            if (distanceToSqr(target) > 0.001) {
                setPos(target);
            }

            if (this.tickCount % 20 == 0) {
                if (level().isEmptyBlock(attached) && level() instanceof ServerLevel serverLevel) {
                    if (getNetworkId() != null) {
                        NetworkRegistry.get(serverLevel).removeNodeFromNetwork(getNetworkId(), getUUID());
                    }
                    if (Config.dropNodeItem) {
                        spawnAtLocation(serverLevel, me.almana.logisticsnetworks.registration.Registration.logisticsNodeItem());
                    }
                    dropFilters();
                    dropUpgrades();
                    discard();
                }
            }
        }
    }

    public void refreshRouteChannels() {
        if (level().isClientSide()) return;
        long bits = isValidNode() && getNetworkId() != null
                ? NodeRouteChannels.encode(channels, false, false, false) : 0L;
        entityData.set(ROUTE_CHANNELS, bits);
    }

    public long getRouteChannels() {
        return entityData.get(ROUTE_CHANNELS);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        return distanceSq < 48.0 * 48.0;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    public void kill(ServerLevel level) {
        if (Config.debugMode) LOGGER.warn(
                "Attempt to kill LogisticsNodeEntity ignored. Please use '/logisticsnetworks removeNodes' or '/ln removeNodes' instead to safely remove nodes.");
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return super.getAddEntityPacket(serverEntity);
    }

    public void setAttachedPos(BlockPos pos) {
        entityData.set(ATTACHED_POS, pos);
        if (capabilityCache != null) {
            capabilityCache.reset();
        }
    }

    public TransferCapabilityCache capabilities() {
        if (capabilityCache == null) {
            capabilityCache = new me.almana.logisticsnetworks.logic.TransferCapabilityCache(this);
        }
        return capabilityCache;
    }

    public BlockPos getAttachedPos() {
        return entityData.get(ATTACHED_POS);
    }

    public void setValid(boolean valid) {
        entityData.set(VALID, valid);
        refreshRouteChannels();
    }

    public boolean isValid() {
        return entityData.get(VALID);
    }

    public boolean isValidNode() {
        return isValid();
    }

    public boolean isActive() {
        return isValidNode() && isAlive();
    }

    @Nullable
    public UUID getNetworkId() {
        return parseOptionalUuid(entityData.get(NETWORK_ID));
    }

    public void setNetworkId(@Nullable UUID networkId) {
        entityData.set(NETWORK_ID, networkId == null ? "" : networkId.toString());
        refreshRouteChannels();
        if (networkId == null) {
            setNetworkName("");
            setNetworkColor(me.almana.logisticsnetworks.data.NetworkColors.DEFAULT);
        }
    }

    public String getNetworkName() {
        return entityData.get(NETWORK_NAME);
    }

    public void setNetworkName(@Nullable String networkName) {
        entityData.set(NETWORK_NAME, networkName == null ? "" : networkName);
    }

    public int getNetworkColor() {
        return entityData.get(NETWORK_COLOR);
    }

    public void setNetworkColor(int color) {
        entityData.set(NETWORK_COLOR, me.almana.logisticsnetworks.data.NetworkColors.mask(color));
    }

    public boolean isRenderVisible() {
        return entityData.get(RENDER_VISIBLE);
    }

    public void setRenderVisible(boolean visible) {
        entityData.set(RENDER_VISIBLE, visible);
    }

    public boolean isHighlighted() {
        return entityData.get(HIGHLIGHTED);
    }

    public void setHighlighted(boolean highlighted) {
        entityData.set(HIGHLIGHTED, highlighted);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return parseOptionalUuid(entityData.get(OWNER_UUID));
    }

    public void setOwnerUUID(@Nullable UUID ownerUuid) {
        entityData.set(OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
    }

    public boolean isOwnedBy(Player player) {
        UUID owner = getOwnerUUID();
        return NodeAccessPolicy.canAccess(owner, player);
    }

    public String getNodeLabel() {
        return entityData.get(NODE_LABEL);
    }

    public void setNodeLabel(@Nullable String label) {
        String sanitized = label == null ? "" : label.trim();
        if (sanitized.length() > 48) {
            sanitized = sanitized.substring(0, 48);
        }
        if (!sanitized.equals(getNodeLabel())) labelRevision = 0;
        entityData.set(NODE_LABEL, sanitized);
    }

    public long getLabelRevision() {
        return labelRevision;
    }

    public void setLabelRevision(long revision) {
        labelRevision = Math.max(0, revision);
    }

    @Nullable
    public ChannelData getChannel(int index) {
        if (index < 0 || index >= CHANNEL_COUNT) {
            return null;
        }
        return channels[index];
    }

    public ChannelData[] getChannels() {
        return channels;
    }

    public ItemStack getUpgradeItem(int slot) {
        if (slot < 0 || slot >= UPGRADE_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        return upgradeItems[slot];
    }

    public void setUpgradeItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < UPGRADE_SLOT_COUNT) {
            int previousTier = NodeUpgradeData.getUpgradeTier(this);
            upgradeItems[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            if (!level().isClientSide()) {
                int tier = NodeUpgradeData.getUpgradeTier(this);
                if (previousTier != tier) {
                    for (int index = 0; index < channels.length; index++) {
                        ChannelData channel = channels[index];
                        NodeUpgradeData.applyTierChange(channel, previousTier, tier);
                        channel.resetResourceRotation();
                        me.almana.logisticsnetworks.network.ServerPayloadHandler.sendChannelSyncToViewers(this, index, channel);
                    }
                }
            }
        }
    }

    public long getLastExecution(int index) {
        return channelCooldowns[index];
    }

    public void setLastExecution(int index, long time) {
        channelCooldowns[index] = time;
    }

    public float getBackoffTicks(int channelIndex) {
        return backoffTicks[channelIndex];
    }

    public void setBackoffTicks(int channelIndex, float value) {
        backoffTicks[channelIndex] = value;
    }

    public void dropUpgrades() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
            ItemStack stack = upgradeItems[i];
            if (!stack.isEmpty()) {
                spawnAtLocation(serverLevel, stack.copy());
                upgradeItems[i] = ItemStack.EMPTY;
            }
        }
    }

    public void dropFilters() {
        for (int channelIndex = 0; channelIndex < CHANNEL_COUNT; channelIndex++) {
            ChannelData channel = channels[channelIndex];
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                channel.setFilterItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
