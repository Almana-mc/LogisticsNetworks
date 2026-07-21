package me.almana.logisticsnetworks.menu;

import me.almana.logisticsnetworks.filter.FilterItemData;
import me.almana.logisticsnetworks.integration.ae2.AE2Compat;
import me.almana.logisticsnetworks.registration.ModTags;
import me.almana.logisticsnetworks.registration.Registration;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PatternSetterMenu extends AbstractContainerMenu {

    private static final int SLOT_PATTERN = 0;
    private static final int SLOT_FILTER = 1;
    private static final int CONTAINER_SLOTS = 2;

    private static final int SLOT_X = 62;
    private static final int PATTERN_SLOT_Y = 28;
    private static final int FILTER_SLOT_Y = 52;

    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;

    private final SimpleContainer container = new SimpleContainer(CONTAINER_SLOTS);
    private final InteractionHand hand;

    // Server-side
    public PatternSetterMenu(int containerId, Inventory playerInv, InteractionHand hand) {
        super(Registration.PATTERN_SETTER_MENU.get(), containerId);
        this.hand = hand;
        layoutSlots(playerInv);
    }

    // Client-side
    public PatternSetterMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(Registration.PATTERN_SETTER_MENU.get(), containerId);
        int handOrdinal = buf.readVarInt();
        this.hand = handOrdinal == InteractionHand.OFF_HAND.ordinal() ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        layoutSlots(playerInv);
    }

    private void layoutSlots(Inventory playerInv) {
        // Pattern slot
        addSlot(new Slot(container, SLOT_PATTERN, SLOT_X, PATTERN_SLOT_Y));

        // Filter slot - only accepts filter items
        addSlot(new Slot(container, SLOT_FILTER, SLOT_X, FILTER_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty() && stack.is(ModTags.FILTERS);
            }
        });

        // Player inventory
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(playerInv, c + r * 9 + 9, PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
            }
        }
        // Hotbar
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(playerInv, c, PLAYER_INV_X + c * 18, PLAYER_INV_Y + 58));
        }
    }

    public void applyPattern(boolean useOutputs, int multiplier, HolderLookup.Provider provider) {
        ItemStack pattern = container.getItem(SLOT_PATTERN);
        ItemStack filter = container.getItem(SLOT_FILTER);

        if (pattern.isEmpty() || filter.isEmpty()) return;
        if (!FilterItemData.isFilterItem(filter)) return;
        if (!AE2Compat.isLoaded()) return;

        int mult = Math.max(1, Math.min(multiplier, 10_000));

        List<AE2Compat.PatternEntry> entries = useOutputs
                ? AE2Compat.readPatternOutputs(pattern)
                : AE2Compat.readPatternInputs(pattern);

        if (entries.isEmpty()) return;

        int capacity = FilterItemData.getCapacity(filter);

        // Clear existing entries
        for (int i = 0; i < capacity; i++) {
            FilterItemData.setEntry(filter, i, ItemStack.EMPTY, provider);
        }

        // Write new entries with multiplier applied
        int count = Math.min(entries.size(), capacity);
        for (int i = 0; i < count; i++) {
            AE2Compat.PatternEntry entry = entries.get(i);
            FilterItemData.setEntry(filter, i, entry.item(), provider);
            FilterItemData.setEntryAmount(filter, i, entry.amount() * mult);
        }

        container.setChanged();
        broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof me.almana.logisticsnetworks.item.PatternSetterItem;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot fromSlot = slots.get(index);
        if (fromSlot == null || !fromSlot.hasItem())
            return ItemStack.EMPTY;

        ItemStack fromStack = fromSlot.getItem();
        ItemStack copy = fromStack.copy();

        if (index < CONTAINER_SLOTS) {
            // Move from container to player inventory
            if (!moveItemStackTo(fromStack, CONTAINER_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Move from player inventory to container
            if (fromStack.is(ModTags.FILTERS)) {
                if (!moveItemStackTo(fromStack, SLOT_FILTER, SLOT_FILTER + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(fromStack, SLOT_PATTERN, SLOT_PATTERN + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (fromStack.isEmpty()) {
            fromSlot.set(ItemStack.EMPTY);
        } else {
            fromSlot.setChanged();
        }

        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, container);
    }
}
