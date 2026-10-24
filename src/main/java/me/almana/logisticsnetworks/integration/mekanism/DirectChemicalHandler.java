package me.almana.logisticsnetworks.integration.mekanism;

import me.almana.logisticsnetworks.integration.storage.StorageEndpoint;
import me.almana.logisticsnetworks.logic.async.ThreadGuard;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class DirectChemicalHandler implements IChemicalHandler {
    private final StorageEndpoint endpoint;
    private final boolean exporting;
    private List<ChemicalStack> exports;

    private DirectChemicalHandler(StorageEndpoint endpoint, boolean exporting) {
        ThreadGuard.requireServerThread();
        this.endpoint = endpoint;
        this.exporting = exporting;
    }

    // verifier-safe, keeps Mekanism optional
    public static IChemicalHandler of(StorageEndpoint endpoint, boolean exporting) {
        return new DirectChemicalHandler(endpoint, exporting);
    }

    public static boolean shareNetwork(IChemicalHandler first, IChemicalHandler second) {
        return first instanceof DirectChemicalHandler a && second instanceof DirectChemicalHandler b
                && a.endpoint.networkIdentity() == b.endpoint.networkIdentity();
    }

    long count(String chemicalId) {
        ThreadGuard.requireServerThread();
        if (!exporting) {
            Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.parse(chemicalId));
            return endpoint.chemicalCount(new ChemicalStack(chemical, 1));
        }
        for (ChemicalStack stack : endpoint.exportableChemicals()) {
            if (chemicalId.equals(ChemicalTransferHelper.getChemicalId(stack))) return stack.getAmount();
        }
        return 0;
    }

    private List<ChemicalStack> exports() {
        ThreadGuard.requireServerThread();
        if (exports == null) exports = exporting ? endpoint.exportableChemicals() : List.of();
        return exports;
    }

    @Override
    public int getChemicalTanks() {
        return Math.max(1, exports().size());
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        if (tank < 0 || tank >= exports().size() || !endpoint.isValid()) return ChemicalStack.EMPTY;
        return exports().get(tank).copy();
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return !stack.isEmpty() && endpoint.isValid();
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        return insertChemical(stack, action);
    }

    @Override
    public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
        ThreadGuard.requireServerThread();
        if (stack.isEmpty() || !endpoint.isValid()) return stack;
        long inserted = endpoint.insertChemical(stack, stack.getAmount(), action.simulate());
        return stack.copyWithAmount(stack.getAmount() - inserted);
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        ThreadGuard.requireServerThread();
        if (!exporting || amount <= 0 || tank < 0 || tank >= exports().size()) return ChemicalStack.EMPTY;
        ChemicalStack stored = exports().get(tank);
        return stored.copyWithAmount(endpoint.extractChemical(stored, amount, action.simulate()));
    }
}
