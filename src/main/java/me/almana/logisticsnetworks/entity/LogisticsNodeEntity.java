package me.almana.logisticsnetworks.entity;

import me.almana.logisticsnetworks.util.ItemStackCompat;

import me.almana.logisticsnetworks.Config;
import me.almana.logisticsnetworks.data.ChannelData;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.integration.ftbteams.FTBTeamsCompat;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import com.mojang.logging.LogUtils;
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
    private static final EntityDataAccessor<Optional<UUID>> NETWORK_ID = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> NETWORK_NAME = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> RENDER_VISIBLE = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> NODE_LABEL = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> HIGHLIGHTED = SynchedEntityData
            .defineId(LogisticsNodeEntity.class, EntityDataSerializers.BOOLEAN);

    private final ChannelData[] channels = new ChannelData[CHANNEL_COUNT];
    private final ItemStack[] upgradeItems = new ItemStack[UPGRADE_SLOT_COUNT];

    private final long[] channelCooldowns = new long[CHANNEL_COUNT];
    private final int[] roundRobinIndex = new int[CHANNEL_COUNT];
    private final float[] backoffTicks = new float[CHANNEL_COUNT];

    public LogisticsNodeEntity(EntityType<LogisticsNodeEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.noPhysics = true;

        for (int i = 0; i < CHANNEL_COUNT; i++) {
            this.channels[i] = new ChannelData();
        }

        Arrays.fill(this.upgradeItems, ItemStack.EMPTY);
    }

    public LogisticsNodeEntity(EntityType<LogisticsNodeEntity> entityType, Level level, BlockPos pos) {
        this(entityType, level);
        this.setPos(Vec3.atCenterOf(pos));
        this.setAttachedPos(pos);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ATTACHED_POS, BlockPos.ZERO);
        this.entityData.define(VALID, false);
        this.entityData.define(NETWORK_ID, Optional.empty());
        this.entityData.define(NETWORK_NAME, "");
        this.entityData.define(RENDER_VISIBLE, true);
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(NODE_LABEL, "");
        this.entityData.define(HIGHLIGHTED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains(KEY_ATTACHED_POS)) {
            setAttachedPos(BlockPos.of(compound.getLong(KEY_ATTACHED_POS)));
        }
        setValid(compound.getBoolean(KEY_VALID));

        if (compound.contains(KEY_NETWORK_ID)) {
            setNetworkId(compound.getUUID(KEY_NETWORK_ID));
        }
        if (compound.contains(KEY_NETWORK_NAME, Tag.TAG_STRING)) {
            setNetworkName(compound.getString(KEY_NETWORK_NAME));
        }
        if (compound.contains(KEY_VISIBLE)) {
            setRenderVisible(compound.getBoolean(KEY_VISIBLE));
        }
        if (compound.contains(KEY_OWNER_UUID)) {
            setOwnerUUID(compound.getUUID(KEY_OWNER_UUID));
        }
        if (compound.contains(KEY_NODE_LABEL, Tag.TAG_STRING)) {
            setNodeLabel(compound.getString(KEY_NODE_LABEL));
        }
        if (compound.contains(KEY_HIGHLIGHTED)) {
            setHighlighted(compound.getBoolean(KEY_HIGHLIGHTED));
        }

        HolderLookup.Provider provider = this.level().registryAccess();

        if (compound.contains(KEY_CHANNELS)) {
            CompoundTag channelsTag = compound.getCompound(KEY_CHANNELS);
            for (int i = 0; i < CHANNEL_COUNT; i++) {
                String key = KEY_CHANNEL_PREFIX + i;
                if (channelsTag.contains(key)) {
                    this.channels[i].load(channelsTag.getCompound(key), provider);
                }
            }
        }

        Arrays.fill(this.upgradeItems, ItemStack.EMPTY);
        if (compound.contains(KEY_UPGRADES, Tag.TAG_LIST)) {
            ListTag upgrades = compound.getList(KEY_UPGRADES, Tag.TAG_COMPOUND);
            for (Tag tag : upgrades) {
                if (tag instanceof CompoundTag entry) {
                    int slot = entry.getInt(KEY_SLOT);
                    if (slot >= 0 && slot < UPGRADE_SLOT_COUNT && entry.contains(KEY_ITEM, Tag.TAG_COMPOUND)) {
                        this.upgradeItems[slot] = ItemStackCompat.parseOptional(provider, entry.getCompound(KEY_ITEM));
                    }
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putLong(KEY_ATTACHED_POS, getAttachedPos().asLong());
        compound.putBoolean(KEY_VALID, isValid());

        UUID netId = getNetworkId();
        if (netId != null) {
            compound.putUUID(KEY_NETWORK_ID, netId);
        }
        String networkName = getNetworkName();
        if (!networkName.isBlank()) {
            compound.putString(KEY_NETWORK_NAME, networkName);
        }
        compound.putBoolean(KEY_VISIBLE, isRenderVisible());

        UUID owner = getOwnerUUID();
        if (owner != null) {
            compound.putUUID(KEY_OWNER_UUID, owner);
        }
        String label = getNodeLabel();
        if (!label.isEmpty()) {
            compound.putString(KEY_NODE_LABEL, label);
        }
        compound.putBoolean(KEY_HIGHLIGHTED, isHighlighted());

        HolderLookup.Provider provider = level().registryAccess();

        CompoundTag channelsTag = new CompoundTag();
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            channelsTag.put(KEY_CHANNEL_PREFIX + i, this.channels[i].save(provider));
        }
        compound.put(KEY_CHANNELS, channelsTag);

        ListTag upgradesTag = new ListTag();
        for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
            if (!this.upgradeItems[i].isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(KEY_SLOT, i);
                entry.put(KEY_ITEM, ItemStackCompat.save(this.upgradeItems[i], provider));
                upgradesTag.add(entry);
            }
        }
        if (!upgradesTag.isEmpty()) {
            compound.put(KEY_UPGRADES, upgradesTag);
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
                if (this.level().isEmptyBlock(attached)) {
                    if (this.getNetworkId() != null && this.level() instanceof ServerLevel serverLevel) {
                        NetworkRegistry.get(serverLevel)
                                .removeNodeFromNetwork(this.getNetworkId(), this.getUUID());
                    }
                    if (Config.dropNodeItem) {
                        this.spawnAtLocation(
                                Registration.LOGISTICS_NODE_ITEM.get());
                    }
                    this.dropFilters();
                    this.dropUpgrades();
                    this.discard();
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
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void kill() {
        LOGGER.warn(
                "Attempt to kill LogisticsNodeEntity ignored. Please use '/logisticsnetworks removeNodes' or '/ln removeNodes' instead to safely remove nodes.");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public void setAttachedPos(BlockPos pos) {
        this.entityData.set(ATTACHED_POS, pos);
    }

    public BlockPos getAttachedPos() {
        return this.entityData.get(ATTACHED_POS);
    }

    public void setValid(boolean valid) {
        this.entityData.set(VALID, valid);
    }

    public boolean isValid() {
        return this.entityData.get(VALID);
    }

    public boolean isValidNode() {
        return isValid();
    }

    public boolean isActive() {
        return isValidNode() && isAlive();
    }

    @Nullable
    public UUID getNetworkId() {
        return this.entityData.get(NETWORK_ID).orElse(null);
    }

    public void setNetworkId(@Nullable UUID networkId) {
        this.entityData.set(NETWORK_ID, Optional.ofNullable(networkId));
        if (networkId == null) {
            setNetworkName("");
        }
    }

    public String getNetworkName() {
        return this.entityData.get(NETWORK_NAME);
    }

    public void setNetworkName(@Nullable String networkName) {
        this.entityData.set(NETWORK_NAME, networkName == null ? "" : networkName);
    }

    public boolean isRenderVisible() {
        return this.entityData.get(RENDER_VISIBLE);
    }

    public void setRenderVisible(boolean visible) {
        this.entityData.set(RENDER_VISIBLE, visible);
    }

    public boolean isHighlighted() {
        return this.entityData.get(HIGHLIGHTED);
    }

    public void setHighlighted(boolean highlighted) {
        this.entityData.set(HIGHLIGHTED, highlighted);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID ownerUuid) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    public boolean isOwnedBy(Player player) {
        UUID owner = getOwnerUUID();
        if (owner == null) return true;
        if (owner.equals(player.getUUID())) return true;
        if (FTBTeamsCompat.isLoaded() && FTBTeamsCompat.arePlayersInSameTeam(owner, player.getUUID())) return true;
        if (player instanceof ServerPlayer sp && sp.hasPermissions(2)) return true;
        return false;
    }

    public String getNodeLabel() {
        return this.entityData.get(NODE_LABEL);
    }

    public void setNodeLabel(@Nullable String label) {
        String sanitized = label == null ? "" : label.trim();
        if (sanitized.length() > 48) {
            sanitized = sanitized.substring(0, 48);
        }
        this.entityData.set(NODE_LABEL, sanitized);
    }

    @Nullable
    public ChannelData getChannel(int index) {
        if (index < 0 || index >= CHANNEL_COUNT)
            return null;
        return channels[index];
    }

    public ChannelData[] getChannels() {
        return channels;
    }

    public ItemStack getUpgradeItem(int slot) {
        if (slot < 0 || slot >= UPGRADE_SLOT_COUNT)
            return ItemStack.EMPTY;
        return upgradeItems[slot];
    }

    public void setUpgradeItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < UPGRADE_SLOT_COUNT) {
            upgradeItems[slot] = stack.isEmpty() ? ItemStack.EMPTY : ItemStackCompat.copyWithCount(stack, 1);
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

    public float getBackoffTicks(int channelIndex) {
        return backoffTicks[channelIndex];
    }

    public void setBackoffTicks(int channelIndex, float value) {
        backoffTicks[channelIndex] = value;
    }

    public void dropUpgrades() {
        for (int i = 0; i < UPGRADE_SLOT_COUNT; i++) {
            ItemStack stack = upgradeItems[i];
            if (!stack.isEmpty()) {
                spawnAtLocation(stack.copy());
                upgradeItems[i] = ItemStack.EMPTY;
            }
        }
    }

    public void dropFilters() {
        for (int channelIndex = 0; channelIndex < CHANNEL_COUNT; channelIndex++) {
            ChannelData channel = channels[channelIndex];
            for (int slot = 0; slot < ChannelData.FILTER_SIZE; slot++) {
                ItemStack stack = channel.getFilterItem(slot);
                if (!stack.isEmpty()) {
                    spawnAtLocation(stack.copy());
                    channel.setFilterItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }

}
