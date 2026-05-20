# Transaction API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate NeoForge transaction-scoped transfers into LogisticsNetworks while preserving current item, fluid, and energy behavior.

**Architecture:** Keep the existing scheduler, channel rules, filter rules, slot masks, backoff, telemetry, and blacklist behavior unchanged. Change only the low-level transfer execution so final mutations can share one `TransactionContext` per logical move, while existing simulation behavior remains available through wrappers.

**Tech Stack:** Java, NeoForge 26.1.2 transfer API, `ResourceHandler<ItemResource>`, `ResourceHandler<FluidResource>`, `EnergyHandler`, `Transaction`, `TransactionContext`.

---

## Scope Guard

This plan does not change channel defaults, GUI behavior, filter semantics, item/fluid/energy batch limits, telemetry units, backoff configuration, or supported integrations.

Do not run `build`, `runClient`, `runServer`, or equivalent runtime commands unless explicitly instructed by the user. Verification steps use source inspection commands only.

## Source Facts

- Project dependency line: `gradle.properties` has `minecraft_version=26.1.2` and `neo_version=26.1.2.48-beta`.
- Current item source lookup: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:359`.
- Current fluid source lookup: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:420`.
- Current energy source lookup: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:469`.
- Current item final move path: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:751-781`.
- Current fluid final move path: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:970-982`.
- Current energy final move path: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:991-1018`.
- Existing all-side combining is in `src/main/java/me/almana/logisticsnetworks/logic/TransferCapabilityCache.java`.

## Files

- Modify: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java`
  - Add `TransactionContext` import.
  - Add `EnergyHandlerUtil` import.
  - Add transaction-aware item/fluid helper overloads.
  - Replace energy move with NeoForge helper.
  - Convert fluid final mutation to a single root transaction.
  - Convert item final mutation to a single root transaction while keeping rollback/void-prevention behavior.
- No new runtime config.
- No changelog entry until after behavior is verified in-game by the maintainer.

---

### Task 1: Add Transaction-Aware Helper Overloads

**Files:**
- Modify: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:27-35`
- Modify: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:818-915`

- [ ] **Step 1: Add imports**

Change the import block from:

```java
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
```

to:

```java
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
```

- [ ] **Step 2: Replace `extractItem` helper with wrapper plus context overload**

Replace the current `extractItem` method with:

```java
    private static ItemStack extractItem(ResourceHandler<ItemResource> handler, int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            ItemStack extracted = extractItem(handler, slot, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return extracted;
        }
    }

    private static ItemStack extractItem(ResourceHandler<ItemResource> handler, int slot, int amount,
            TransactionContext transaction) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int request = Math.min(amount, resource.getMaxStackSize());
        int extracted = handler.extract(slot, resource, request, transaction);
        return extracted <= 0 ? ItemStack.EMPTY : resource.toStack(extracted);
    }
```

- [ ] **Step 3: Replace `insertItem` helper with wrapper plus context overload**

Replace the current `insertItem` method with:

```java
    private static ItemStack insertItem(ResourceHandler<ItemResource> handler, int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            ItemStack remaining = insertItem(handler, slot, stack, tx);
            if (!simulate) {
                tx.commit();
            }
            return remaining;
        }
    }

    private static ItemStack insertItem(ResourceHandler<ItemResource> handler, int slot, ItemStack stack,
            TransactionContext transaction) {
        return ItemUtil.insertItemReturnRemaining(handler, slot, stack, false, transaction);
    }
```

- [ ] **Step 4: Replace fluid helper methods with wrappers plus context overloads**

Replace `fillFluid` and `drainFluid` with:

```java
    private static int fillFluid(ResourceHandler<FluidResource> handler, FluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return 0;
        }

        try (var tx = Transaction.openRoot()) {
            int inserted = fillFluid(handler, stack, tx);
            if (!simulate) {
                tx.commit();
            }
            return inserted;
        }
    }

    private static int fillFluid(ResourceHandler<FluidResource> handler, FluidStack stack,
            TransactionContext transaction) {
        if (stack.isEmpty()) {
            return 0;
        }

        return handler.insert(FluidResource.of(stack), stack.getAmount(), transaction);
    }

    private static FluidStack drainFluid(ResourceHandler<FluidResource> handler, FluidStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            FluidStack extracted = drainFluid(handler, stack, tx);
            if (!simulate) {
                tx.commit();
            }
            return extracted;
        }
    }

    private static FluidStack drainFluid(ResourceHandler<FluidResource> handler, FluidStack stack,
            TransactionContext transaction) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }

        FluidResource resource = FluidResource.of(stack);
        int extracted = handler.extract(resource, stack.getAmount(), transaction);
        return extracted <= 0 ? FluidStack.EMPTY : resource.toStack(extracted);
    }
```

- [ ] **Step 5: Add transaction-aware `insertItemWithAllowedSlots` overload**

Keep the existing boolean wrapper signature, but change its body to:

```java
    private static ItemStack insertItemWithAllowedSlots(ResourceHandler<ItemResource> handler, ItemStack stack,
            boolean simulate, boolean[] allowedSlots) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        try (var tx = Transaction.openRoot()) {
            ItemStack remaining = insertItemWithAllowedSlots(handler, stack, tx, allowedSlots);
            if (!simulate) {
                tx.commit();
            }
            return remaining;
        }
    }
```

Add this overload directly below it:

```java
    private static ItemStack insertItemWithAllowedSlots(ResourceHandler<ItemResource> handler, ItemStack stack,
            TransactionContext transaction, boolean[] allowedSlots) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (allowedSlots == null) {
            return ItemUtil.insertItemReturnRemaining(handler, stack, false, transaction);
        }

        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < handler.size() && !remaining.isEmpty(); slot++) {
            if (slot >= allowedSlots.length || !allowedSlots[slot]) {
                continue;
            }
            ItemStack slotStack = ItemUtil.getStack(handler, slot);
            if (slotStack.isEmpty()) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                continue;
            }
            if (!handler.isValid(slot, ItemResource.of(remaining))) {
                continue;
            }
            remaining = insertItem(handler, slot, remaining, transaction);
        }

        for (int slot = 0; slot < handler.size() && !remaining.isEmpty(); slot++) {
            if (slot >= allowedSlots.length || !allowedSlots[slot]) {
                continue;
            }
            ItemStack slotStack = ItemUtil.getStack(handler, slot);
            if (!slotStack.isEmpty()) {
                continue;
            }
            if (!handler.isValid(slot, ItemResource.of(remaining))) {
                continue;
            }
            remaining = insertItem(handler, slot, remaining, transaction);
        }

        return remaining;
    }
```

- [ ] **Step 6: Verify helper call sites still compile structurally**

Run:

```powershell
rg -n "extractItem\\(|insertItem\\(|fillFluid\\(|drainFluid\\(|insertItemWithAllowedSlots\\(" src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
```

Expected:

```text
All old boolean call sites still resolve to wrapper methods.
New TransactionContext overloads exist in the same file.
No caller passes null as a TransactionContext.
```

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
git commit -m "refactor: add transaction-aware transfer helpers"
```

---

### Task 2: Use NeoForge Energy Move Helper

**Files:**
- Modify: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:991-1018`

- [ ] **Step 1: Replace energy move implementation**

Replace:

```java
    private static int executeEnergyMove(EnergyHandler source, EnergyHandler target, int limitRF) {
        int toMove;
        try (var tx = Transaction.openRoot()) {
            int extracted = source.extract(limitRF, tx);
            if (extracted <= 0) {
                return 0;
            }
            int accepted = target.insert(extracted, tx);
            toMove = Math.min(extracted, accepted);
        }

        if (toMove <= 0) {
            return 0;
        }

        try (var tx = Transaction.openRoot()) {
            int extracted = source.extract(toMove, tx);
            if (extracted <= 0) {
                return 0;
            }
            int inserted = target.insert(extracted, tx);
            if (inserted != extracted) {
                return 0;
            }
            tx.commit();
            return inserted;
        }
    }
```

with:

```java
    private static int executeEnergyMove(EnergyHandler source, EnergyHandler target, int limitRF) {
        return EnergyHandlerUtil.move(source, target, limitRF, null);
    }
```

- [ ] **Step 2: Verify import is used**

Run:

```powershell
rg -n "EnergyHandlerUtil|executeEnergyMove" src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
```

Expected:

```text
EnergyHandlerUtil is imported and used once.
executeEnergyMove still has the same method signature.
```

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
git commit -m "refactor: use NeoForge energy transfer helper"
```

---

### Task 3: Make Fluid Final Mutation Transaction-Scoped

**Files:**
- Modify: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:970-982`

- [ ] **Step 1: Replace separate final drain/fill/rollback block**

Replace:

```java
            FluidStack drained = drainFluid(source, simulated.copyWithAmount(toMove), false);
            if (drained.isEmpty())
                continue;

            int filled = fillFluid(target, drained, false);
            if (filled < drained.getAmount()) {
                int rollbackAmount = drained.getAmount() - filled;
                int returned = fillFluid(source, drained.copyWithAmount(rollbackAmount), false);
                if (returned < rollbackAmount) {
                    LOGGER.error("FLUID VOIDING: Source rejected rollback of {} mB ({}). {} mB lost.",
                            rollbackAmount - returned, drained.getFluid(), rollbackAmount - returned);
                }
            }
```

with:

```java
            int filled;
            try (var tx = Transaction.openRoot()) {
                FluidStack drained = drainFluid(source, simulated.copyWithAmount(toMove), tx);
                if (drained.isEmpty()) {
                    continue;
                }

                filled = fillFluid(target, drained, tx);
                if (filled < drained.getAmount()) {
                    int rollbackAmount = drained.getAmount() - filled;
                    int returned = fillFluid(source, drained.copyWithAmount(rollbackAmount), tx);
                    if (returned < rollbackAmount) {
                        LOGGER.error("FLUID VOIDING: Source rejected rollback of {} mB ({}). {} mB lost.",
                                rollbackAmount - returned, drained.getFluid(), rollbackAmount - returned);
                    }
                }

                tx.commit();
            }
```

- [ ] **Step 2: Preserve current fluid behavior**

Check these facts in the changed block:

```text
If source extracts nothing, the loop continues.
If target accepts all drained fluid, the same amount is counted as moved.
If target accepts only part, the code still tries to return the remainder to the source.
If source rejects remainder, the existing error log string is unchanged.
`remaining -= filled` still happens after the transaction block.
```

- [ ] **Step 3: Verify source inspection**

Run:

```powershell
rg -n "FLUID VOIDING|drainFluid\\(source, simulated.copyWithAmount\\(toMove\\)|fillFluid\\(target, drained" src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
```

Expected:

```text
The FLUID VOIDING log still exists.
The final drain uses the TransactionContext overload.
The final fill uses the TransactionContext overload.
```

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
git commit -m "refactor: scope fluid moves to one transaction"
```

---

### Task 4: Make Item Final Mutation Transaction-Scoped

**Files:**
- Modify: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java:751-781`

- [ ] **Step 1: Replace separate final extraction/insertion block**

Replace:

```java
                    ItemStack toMove = extractItem(source, slot, acceptableCount, false);
                    if (toMove.isEmpty()) {
                        continue;
                    }

                    ItemStack uninserted = insertItemWithAllowedSlots(target.handler(), toMove, false,
                            target.allowedSlots());
                    int targetAccepted = toMove.getCount() - uninserted.getCount();
                    int droppedToWorld = 0;

                    if (!uninserted.isEmpty()) {
                        ItemStack stillLeft = insertItem(source, slot, uninserted, false);
                        if (!stillLeft.isEmpty()) {
                            for (int fallback = 0; fallback < source.size() && !stillLeft.isEmpty(); fallback++) {
                                stillLeft = insertItem(source, fallback, stillLeft, false);
                            }
                            if (!stillLeft.isEmpty()) {
                                ItemStack forcedRemainder = insertItemWithAllowedSlots(target.handler(), stillLeft,
                                        false, target.allowedSlots());
                                int forcedIn = stillLeft.getCount() - forcedRemainder.getCount();
                                targetAccepted += forcedIn;
                                if (!forcedRemainder.isEmpty()) {
                                    LOGGER.error("ITEM VOIDING PREVENTED: Could not return {} to source or fit into "
                                            + "target slot mask. Dropping at source pos {}.",
                                            forcedRemainder, sourcePos);
                                    droppedToWorld = forcedRemainder.getCount();
                                    Block.popResource(sourceLevel, sourcePos, forcedRemainder);
                                }
                            }
                        }
                    }
```

with:

```java
                    int targetAccepted;
                    int droppedToWorld = 0;
                    ItemStack dropStack = ItemStack.EMPTY;

                    try (var tx = Transaction.openRoot()) {
                        ItemStack toMove = extractItem(source, slot, acceptableCount, tx);
                        if (toMove.isEmpty()) {
                            continue;
                        }

                        ItemStack uninserted = insertItemWithAllowedSlots(target.handler(), toMove, tx,
                                target.allowedSlots());
                        targetAccepted = toMove.getCount() - uninserted.getCount();

                        if (!uninserted.isEmpty()) {
                            ItemStack stillLeft = insertItem(source, slot, uninserted, tx);
                            if (!stillLeft.isEmpty()) {
                                for (int fallback = 0; fallback < source.size() && !stillLeft.isEmpty(); fallback++) {
                                    stillLeft = insertItem(source, fallback, stillLeft, tx);
                                }
                                if (!stillLeft.isEmpty()) {
                                    ItemStack forcedRemainder = insertItemWithAllowedSlots(target.handler(), stillLeft,
                                            tx, target.allowedSlots());
                                    int forcedIn = stillLeft.getCount() - forcedRemainder.getCount();
                                    targetAccepted += forcedIn;
                                    if (!forcedRemainder.isEmpty()) {
                                        LOGGER.error("ITEM VOIDING PREVENTED: Could not return {} to source or fit into "
                                                + "target slot mask. Dropping at source pos {}.",
                                                forcedRemainder, sourcePos);
                                        droppedToWorld = forcedRemainder.getCount();
                                        dropStack = forcedRemainder.copy();
                                    }
                                }
                            }
                        }

                        tx.commit();
                    }

                    if (!dropStack.isEmpty()) {
                        Block.popResource(sourceLevel, sourcePos, dropStack);
                    }
```

- [ ] **Step 2: Preserve current item behavior**

Check these facts in the changed block:

```text
Target accepted count is still computed from `toMove - uninserted`.
Uninserted items still try the original source slot first.
Fallback return still scans all source slots in order.
Forced target insertion still happens before world drop.
The ITEM VOIDING PREVENTED log string is unchanged.
World drop happens only after the transaction commits.
`sourceLost = targetAccepted + droppedToWorld` remains unchanged below the block.
```

- [ ] **Step 3: Verify source inspection**

Run:

```powershell
rg -n "ITEM VOIDING PREVENTED|dropStack|sourceLost = targetAccepted \\+ droppedToWorld|extractItem\\(source, slot, acceptableCount, tx\\)" src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
```

Expected:

```text
The ITEM VOIDING PREVENTED log still exists.
The final extraction uses the TransactionContext overload.
`dropStack` exists so world drops occur after commit.
`sourceLost = targetAccepted + droppedToWorld` still exists.
```

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
git commit -m "refactor: scope item moves to one transaction"
```

---

### Task 5: Keep Simulation Paths Separate From Final Mutation

**Files:**
- Inspect: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java`

- [ ] **Step 1: Verify simulations still roll back**

Run:

```powershell
rg -n "extractItem\\(source, slot, remaining, true\\)|insertItemWithAllowedSlots\\(target.handler\\(\\), simulatedInsert, true|drainFluid\\(source, tankFluid.copyWithAmount\\(requestFromTank\\), true\\)|fillFluid\\(target, simulated.copyWithAmount\\(request\\), true\\)" src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
```

Expected:

```text
Item simulated extraction still uses `simulate=true`.
Item simulated insertion still uses `simulate=true`.
Fluid simulated drain still uses `simulate=true`.
Fluid simulated fill still uses `simulate=true`.
```

- [ ] **Step 2: Verify final mutations use transaction context**

Run:

```powershell
rg -n "extractItem\\(source, slot, acceptableCount, tx\\)|insertItemWithAllowedSlots\\(target.handler\\(\\), toMove, tx|drainFluid\\(source, simulated.copyWithAmount\\(toMove\\), tx\\)|fillFluid\\(target, drained, tx\\)" src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
```

Expected:

```text
Item final extraction and final insertion use `tx`.
Fluid final drain and final fill use `tx`.
```

- [ ] **Step 3: Verify no default settings changed**

Run:

```powershell
git diff -- gradle.properties src/main/resources src/main/templates
```

Expected:

```text
No changes are shown.
```

- [ ] **Step 4: Commit if inspection changes were needed**

If Task 5 required edits:

```powershell
git add src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
git commit -m "chore: preserve transfer simulation paths"
```

If no edits were needed:

```powershell
git status --short
```

Expected:

```text
No new changes from Task 5.
```

---

### Task 6: Manual Diff Review For Behavior Preservation

**Files:**
- Inspect: `src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java`

- [ ] **Step 1: Review only transfer engine changes**

Run:

```powershell
git diff -- src/main/java/me/almana/logisticsnetworks/logic/TransferEngine.java
```

Expected:

```text
Only helper overloads and final mutation transaction scoping changed.
No filter condition changed.
No threshold condition changed.
No batch arithmetic changed.
No target ordering changed.
No node reachability logic changed.
No telemetry update path changed.
No backoff path changed.
```

- [ ] **Step 2: Review forbidden command compliance**

Run:

```powershell
git status --short
```

Expected:

```text
No generated run/build output files are present.
Only source files and this plan are changed if commits were not made.
```

- [ ] **Step 3: Commit the plan if not already committed**

```powershell
git add docs/superpowers/plans/2026-05-20-transaction-api-integration.md
git commit -m "docs: plan transaction API integration"
```

---

## Post-Implementation Maintainer Verification

These are in-game checks for a maintainer to run only after explicitly choosing to run a client/server. They are not part of agent execution under the current restrictions.

- Item export into a normal chest preserves stack order and batch limit.
- Item export into a nearly-full chest moves only accepted items and does not void leftovers.
- Item export with slot filter still inserts only allowed target slots.
- Item export/import amount thresholds still stop movement at the same counts.
- Fluid export into a normal tank preserves mB moved and batch limit.
- Fluid export into a nearly-full tank returns or preserves excess fluid.
- Fluid blacklist still blocks tagged fluids.
- Energy transfer still respects batch limit and returns the same moved amount as before.
- All-side setting still aggregates every exposed side.
- Backoff still behaves the same when every reachable target rejects transfer.

## Self-Review

- Spec coverage: The plan covers NeoForge transaction integration for item, fluid, and energy transfer while preserving current systems and defaults.
- Placeholder scan: No task relies on unspecified implementation work.
- Type consistency: All new overloads use existing NeoForge `TransactionContext`, existing `ResourceHandler<ItemResource>`, existing `ResourceHandler<FluidResource>`, and existing `EnergyHandler`.
- Risk note: The item and fluid tasks intentionally preserve existing fallback behavior instead of replacing it with pure all-or-nothing movement. This keeps observed behavior stable while reducing transaction fragmentation.
