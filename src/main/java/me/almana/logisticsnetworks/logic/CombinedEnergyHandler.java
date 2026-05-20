package me.almana.logisticsnetworks.logic;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

final class CombinedEnergyHandler implements EnergyHandler {
    private final EnergyHandler[] handlers;

    CombinedEnergyHandler(EnergyHandler[] handlers) {
        this.handlers = handlers;
    }

    @Override
    public long getAmountAsLong() {
        long sum = 0;
        for (EnergyHandler handler : handlers) {
            sum += handler.getAmountAsLong();
        }
        return sum;
    }

    @Override
    public long getCapacityAsLong() {
        long sum = 0;
        for (EnergyHandler handler : handlers) {
            sum += handler.getCapacityAsLong();
        }
        return sum;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        int remaining = amount;
        int total = 0;
        for (EnergyHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            int accepted = handler.insert(remaining, transaction);
            if (accepted > 0) {
                total += accepted;
                remaining -= accepted;
            }
        }
        return total;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }
        int remaining = amount;
        int total = 0;
        for (EnergyHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            int extracted = handler.extract(remaining, transaction);
            if (extracted > 0) {
                total += extracted;
                remaining -= extracted;
            }
        }
        return total;
    }
}
