package me.almana.logisticsnetworks.entity;

import com.mojang.logging.LogUtils;
import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public class LogisticsNodeEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int UPGRADE_SLOT_COUNT = 4;
    public static final int CHANNEL_COUNT = 9;

    private static final String KEY_ATTACHED_POS = "AttachedPos";
    private static final String KEY_VALID = "Valid";
    private static final String KEY_NETWORK_ID = "NetworkId";
    private static final String KEY_NETWORK_NAME = "NetworkName";
    private static final String KEY_VISIBLE = "RenderVisible";
    private static final String KEY_CHANNELS = "Channels";
    private static final String KEY_UPGRADES = "Upgrades";
    private static final String KEY_CHANNEL_PREFIX = "Channel";
    private static final String KEY_SLOT = "Slot";
    private static final String KEY_ITEM = "Item";
    private static final String KEY_OWNER_UUID = "OwnerUUID";
    private static final String KEY_NODE_LABEL = "NodeLabel";
    private static final String KEY_HIGHLIGHTED = "Highlighted";

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

    private final ChannelData[] channels = new ChannelData[CHANNEL_COUNT];
    private final ItemStack[] upgradeItems = new ItemStack[UPGRADE_SLOT_COUNT];

    private final long[] channelCooldowns = new long[CHANNEL_COUNT];
    private final int[] roundRobinIndex = new int[CHANNEL_COUNT];
    private final float[] backoffTicks = new float[CHANNEL_COUNT];
    private final int[] recipeCursorEntry = new int[CHANNEL_COUNT];
    private final int[] recipeCursorRemaining = new int[CHANNEL_COUNT];

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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        if (input.getLong(KEY_ATTACHED_POS).isPresent()) {
            setAttachedPos(BlockPos.of(input.getLongOr(KEY_ATTACHED_POS, BlockPos.ZERO.asLong())));
        }
        setValid(input.getBooleanOr(KEY_VALID, false));
        setNetworkId(parseOptionalUuid(input.getStringOr(KEY_NETWORK_ID, "")));
        setNetworkName(input.getStringOr(KEY_NETWORK_NAME, ""));
        setRenderVisible(input.getBooleanOr(KEY_VISIBLE, true));
        setOwnerUUID(parseOptionalUuid(input.getStringOr(KEY_OWNER_UUID, "")));
        setNodeLabel(input.getStringOr(KEY_NODE_LABEL, ""));
        setHighlighted(input.getBooleanOr(KEY_HIGHLIGHTED, false));

        ValueInput channelsInput = input.childOrEmpty(KEY_CHANNELS);
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            channels[i].load(channelsInput.childOrEmpty(KEY_CHANNEL_PREFIX + i));
        }

        Arrays.fill(upgradeItems, ItemStack.EMPTY);
        for (ValueInput entry : input.childrenListOrEmpty(KEY_UPGRADES)) {
            int slot = entry.getIntOr(KEY_SLOT, -1);
            if (slot < 0 || slot >= UPGRADE_SLOT_COUNT) {
                continue;
            }
            upgradeItems[slot] = entry.read(KEY_ITEM, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putLong(KEY_ATTACHED_POS, getAttachedPos().asLong());
        output.putBoolean(KEY_VALID, isValid());

        UUID netId = getNetworkId();
        if (netId != null) {
            output.putString(KEY_NETWORK_ID, netId.toString());
        }
        String networkName = getNetworkName();
        if (!networkName.isBlank()) {
            output.putString(KEY_NETWORK_NAME, networkName);
        }
        output.putBoolean(KEY_VISIBLE, isRenderVisible());

        UUID owner = getOwnerUUID();
        if (owner != null) {
            output.putString(KEY_OWNER_UUID, owner.toString());
        }
        String label = getNodeLabel();
        if (!label.isEmpty()) {
            output.putString(KEY_NODE_LABEL, label);
        }
        output.putBoolean(KEY_HIGHLIGHTED, isHighlighted());

        ValueOutput channelsOutput = output.child(KEY_CHANNELS);
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            channels[i].save(channelsOutput.child(KEY_CHANNEL_PREFIX + i));
        }

        var upgradesOutput = output.childrenList(KEY_UPGRADES);
        for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
            if (upgradeItems[i].isEmpty()) {
                continue;
            }
            ValueOutput entry = upgradesOutput.addChild();
            entry.putInt(KEY_SLOT, i);
            entry.store(KEY_ITEM, ItemStack.OPTIONAL_CODEC, upgradeItems[i]);
        }
    }

    @Override
    public void tick() {
        if (this.level().isClientSide()) return;

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
        LOGGER.warn(
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
    }

    public BlockPos getAttachedPos() {
        return entityData.get(ATTACHED_POS);
    }

    public void setValid(boolean valid) {
        entityData.set(VALID, valid);
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
        if (networkId == null) {
            setNetworkName("");
        }
    }

    public String getNetworkName() {
        return entityData.get(NETWORK_NAME);
    }

    public void setNetworkName(@Nullable String networkName) {
        entityData.set(NETWORK_NAME, networkName == null ? "" : networkName);
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
        if (owner == null) return true;
        if (owner.equals(player.getUUID())) return true;
        if (FTBTeamsCompat.isLoaded() && FTBTeamsCompat.arePlayersInSameTeam(owner, player.getUUID())) return true;
        if (player instanceof ServerPlayer sp && sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return true;
        return false;
    }

    public String getNodeLabel() {
        return entityData.get(NODE_LABEL);
    }

    public void setNodeLabel(@Nullable String label) {
        String sanitized = label == null ? "" : label.trim();
        if (sanitized.length() > 48) {
            sanitized = sanitized.substring(0, 48);
        }
        entityData.set(NODE_LABEL, sanitized);
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
            upgradeItems[slot] = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }

    public long getLastExecution(int index) {
        return channelCooldowns[index];
    }

    public void setLastExecution(int index, long time) {
        channelCooldowns[index] = time;
    }

    public int getRoundRobinIndex(int channelIndex) {
        return roundRobinIndex[channelIndex];
    }

    public void advanceRoundRobin(int channelIndex, int targetCount) {
        if (targetCount > 0) {
            roundRobinIndex[channelIndex] = (roundRobinIndex[channelIndex] + 1) % targetCount;
        }
    }

    public void advanceRoundRobin(int channelIndex, int targetCount, int steps) {
        if (targetCount > 0 && steps > 0) {
            roundRobinIndex[channelIndex] = (roundRobinIndex[channelIndex] + steps) % targetCount;
        }
    }

    public float getBackoffTicks(int channelIndex) {
        return backoffTicks[channelIndex];
    }

    public void setBackoffTicks(int channelIndex, float value) {
        backoffTicks[channelIndex] = value;
    }

    public int getRecipeCursorEntry(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= CHANNEL_COUNT) return 0;
        return recipeCursorEntry[channelIndex];
    }

    public int getRecipeCursorRemaining(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= CHANNEL_COUNT) return 0;
        return recipeCursorRemaining[channelIndex];
    }

    public void setRecipeCursor(int channelIndex, int entryIndex, int remaining) {
        if (channelIndex >= 0 && channelIndex < CHANNEL_COUNT) {
            recipeCursorEntry[channelIndex] = entryIndex;
            recipeCursorRemaining[channelIndex] = remaining;
        }
    }

    public void resetRecipeCursor(int channelIndex) {
        if (channelIndex >= 0 && channelIndex < CHANNEL_COUNT) {
            recipeCursorEntry[channelIndex] = 0;
            recipeCursorRemaining[channelIndex] = 0;
        }
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
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int channelIndex = 0; channelIndex < CHANNEL_COUNT; channelIndex++) {
            ChannelData channel = channels[channelIndex];
            if (channel == null) {
                continue;
            }
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                ItemStack stack = channel.getFilterItem(slot);
                if (!stack.isEmpty()) {
                    spawnAtLocation(serverLevel, stack.copy());
                    channel.setFilterItem(slot, ItemStack.EMPTY);
                }
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
