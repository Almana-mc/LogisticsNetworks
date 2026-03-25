package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.block.ComputerBlockEntity;
import me.almana.logisticsnetworks.data.LogisticsNetwork;
import me.almana.logisticsnetworks.data.NetworkRegistry;
import me.almana.logisticsnetworks.item.WrenchItem;
import me.almana.logisticsnetworks.network.SyncNetworkListPayload;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ComputerMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int WRENCH_SLOT_X = 291;
    private static final int WRENCH_SLOT_Y = 8;

    private final BlockPos computerPos;
    private ItemStack wrenchStack = ItemStack.EMPTY;
    private final Container wrenchContainer;
    private boolean wrenchSlotActive = true;

    public ComputerMenu(int containerId, Inventory playerInv, BlockPos computerPos) {
        super(Registration.COMPUTER_MENU.get(), containerId);
        this.computerPos = computerPos;
        this.wrenchContainer = new WrenchSlotContainer();
        loadPlayerWrench(playerInv);

        layoutSlots();
    }

    public ComputerMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(Registration.COMPUTER_MENU.get(), containerId);
        this.computerPos = buf.readBlockPos();
        this.wrenchContainer = new WrenchSlotContainer();

        layoutSlots();
    }

    private void loadPlayerWrench(Inventory playerInv) {
        for (int slot = 0; slot < playerInv.getContainerSize(); slot++) {
            ItemStack stack = playerInv.getItem(slot);
            if (!(stack.getItem() instanceof WrenchItem)) {
                continue;
            }

            wrenchStack = stack.copyWithCount(1);
            stack.shrink(1);
            playerInv.setChanged();
            return;
        }
    }

    private void layoutSlots() {
        // Single wrench slot on the right side
        addSlot(new Slot(wrenchContainer, 0, WRENCH_SLOT_X, WRENCH_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof WrenchItem;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean isActive() {
                return wrenchSlotActive;
            }
        });
    }

    public void requestNetworkList(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level))
            return;

        NetworkRegistry registry = NetworkRegistry.get(level);
        List<LogisticsNetwork> networks = registry.getNetworksForPlayer(player.getUUID());
        ComputerBlockEntity computer = getComputer(level);
        Set<UUID> starredNetworks = computer != null ? computer.getStarredNetworks() : Set.of();

        LOGGER.debug("Player {} UUID: {}", player.getName().getString(), player.getUUID());
        LOGGER.debug("Found {} networks for player", networks.size());

        List<SyncNetworkListPayload.NetworkEntry> entries = new ArrayList<>();
        for (LogisticsNetwork net : networks) {
            LOGGER.debug("  Network: {} (ID: {}, Nodes: {}, Owner: {})",
                    net.getName(), net.getId(), net.getNodeUuids().size(), net.getOwnerUuid());
            entries.add(new SyncNetworkListPayload.NetworkEntry(
                    net.getId(),
                    net.getName(),
                    net.getNodeUuids().size(),
                    starredNetworks.contains(net.getId())));
        }

        LOGGER.debug("Sending {} network entries to client", entries.size());
        PacketDistributor.sendToPlayer(player, new SyncNetworkListPayload(entries));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot fromSlot = slots.get(index);
        if (fromSlot == null || !fromSlot.hasItem())
            return ItemStack.EMPTY;

        ItemStack fromStack = fromSlot.getItem();
        ItemStack copy = fromStack.copy();

        if (index == 0) {
            return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(computerPos.getX() + 0.5, computerPos.getY() + 0.5,
                computerPos.getZ() + 0.5) < 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!wrenchStack.isEmpty() && !player.level().isClientSide) {
            player.getInventory().placeItemBackInInventory(wrenchStack);
            wrenchStack = ItemStack.EMPTY;
        }
    }

    public void setWrenchSlotActive(boolean active) {
        this.wrenchSlotActive = active;
    }

    public BlockPos getComputerPos() {
        return computerPos;
    }

    private ComputerBlockEntity getComputer(ServerLevel level) {
        if (level.getBlockEntity(computerPos) instanceof ComputerBlockEntity computer) {
            return computer;
        }
        return null;
    }

    private class WrenchSlotContainer implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return wrenchStack.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? wrenchStack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot == 0 && !wrenchStack.isEmpty()) {
                ItemStack result = wrenchStack.split(amount);
                if (wrenchStack.isEmpty()) {
                    wrenchStack = ItemStack.EMPTY;
                }
                return result;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot == 0) {
                ItemStack result = wrenchStack;
                wrenchStack = ItemStack.EMPTY;
                return result;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot == 0) {
                wrenchStack = stack.copyWithCount(1); // Only 1 wrench
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            wrenchStack = ItemStack.EMPTY;
        }
    }
}
