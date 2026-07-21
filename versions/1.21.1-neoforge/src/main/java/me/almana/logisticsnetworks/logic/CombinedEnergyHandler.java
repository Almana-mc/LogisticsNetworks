package me.almana.logisticsnetworks.logic;

import net.neoforged.neoforge.energy.IEnergyStorage;

final class CombinedEnergyHandler implements IEnergyStorage {
    private final IEnergyStorage[] handlers;

    CombinedEnergyHandler(IEnergyStorage[] handlers) {
        this.handlers = handlers;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        int remaining = maxReceive;
        int total = 0;
        for (IEnergyStorage handler : handlers) {
            if (remaining <= 0) break;
            if (!handler.canReceive()) continue;
            int accepted = handler.receiveEnergy(remaining, simulate);
            if (accepted > 0) {
                total += accepted;
                remaining -= accepted;
            }
        }
        return total;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) return 0;
        int remaining = maxExtract;
        int total = 0;
        for (IEnergyStorage handler : handlers) {
            if (remaining <= 0) break;
            if (!handler.canExtract()) continue;
            int extracted = handler.extractEnergy(remaining, simulate);
            if (extracted > 0) {
                total += extracted;
                remaining -= extracted;
            }
        }
        return total;
    }

    @Override
    public int getEnergyStored() {
        long sum = 0;
        for (IEnergyStorage handler : handlers) {
            sum += handler.getEnergyStored();
        }
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    @Override
    public int getMaxEnergyStored() {
        long sum = 0;
        for (IEnergyStorage handler : handlers) {
            sum += handler.getMaxEnergyStored();
        }
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    @Override
    public boolean canExtract() {
        for (IEnergyStorage handler : handlers) {
            if (handler.canExtract()) return true;
        }
        return false;
    }

    @Override
    public boolean canReceive() {
        for (IEnergyStorage handler : handlers) {
            if (handler.canReceive()) return true;
        }
        return false;
    }
}
