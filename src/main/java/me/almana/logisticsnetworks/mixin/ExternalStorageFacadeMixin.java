package me.almana.logisticsnetworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

// AE2 always opens root
@Pseudo
@Mixin(targets = "appeng.me.storage.ExternalStorageFacade$ResourceHandlerFacade")
public abstract class ExternalStorageFacadeMixin {

    @SuppressWarnings("deprecation")
    @WrapOperation(method = {"insertExternal", "extractExternal", "getAvailableStacks"},
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/transfer/transaction/Transaction;openRoot()Lnet/neoforged/neoforge/transfer/transaction/Transaction;"))
    private Transaction logisticsnetworks$joinOpenTransaction(Operation<Transaction> original) {
        TransactionContext open = Transaction.getCurrentOpenedTransaction();
        return open == null ? original.call() : Transaction.open(open);
    }
}
