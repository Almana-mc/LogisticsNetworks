package me.almana.logisticsnetworks.logic;

import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

public interface RecipeLane {

    int available(int cap);

    int fit(int target, int sets);

    boolean move(int target, int sets, @Nullable TransactionContext tx);

    void rollback(int target);

    void commit(int target, @Nullable RecipeSets.Listener listener);
}
