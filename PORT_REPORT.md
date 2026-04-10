# Port Report: 1.21.1 → 26.1

## Source Commits
- `b003555` — feature port, workflow port (bulk)
- `38498ea` — version bump (1.6.0 → 1.7.0)
- `01337a6` — rename changelog file
- `a435054` — conditional copyJar in build.gradle.kts
- `0bebb3d` — build flow fix (file() → File())

## Files Changed (25 files + 6 new)

### New Files Created
| File | Description |
|------|-------------|
| `filter/SlotExpressionUtil.java` | Slot expression parsing/formatting utility |
| `network/OpenNodeFilterPayload.java` | Payload: open filter from node screen |
| `network/SetChannelNamePayload.java` | Payload: set channel name (24 char limit) |
| `network/SetFilterEntryEnchantedPayload.java` | Payload: set/clear enchanted constraint |
| `network/SetFilterEntrySlotMappingPayload.java` | Payload: set slot mapping expression |
| `CHANGELOGS/26.1-1.1.0.md` | Changelog for 1.1.0 |

### Modified Files
| File | Changes |
|------|---------|
| `data/ChannelData.java` | Added `name` field, `@Nullable ioDirection`, "all" direction serialization (CompoundTag + ValueInput/ValueOutput) |
| `data/NodeClipboardConfig.java` | Nullable direction + name in capture/apply/serialize/deserialize |
| `filter/FilterItemData.java` | batch/stock replacing amount, slotMapping, enchanted per-entry, containsItemFullInSlot, getItemBatchLimitFull, checkEnchantedConstraint, clearEntryItem, isNbtOnlySlot update |
| `filter/NbtFilterData.java` | Added `parseValueString()` for SNBT value parsing |
| `filter/SlotFilterData.java` | Delegated to SlotExpressionUtil |
| `item/SlotFilterItem.java` | Added deprecation tooltip lines |
| `logic/FilterLogic.java` | Added `matchesItemInSlot()` with slot mapping support |
| `logic/TransferEngine.java` | find*Handler null-direction probing, RecipeEntry.amount→batch, matchesItemInSlot calls, batchMoved tracking, getPerEntryBatchLimit |
| `menu/FilterMenu.java` | Node filter constructor, -2 ordinal FriendlyByteBuf, batch/stock/enchanted/slotMapping methods, clearFilterEntryItem, addSlotNbtRule fallbackValue overload, getOpenedStack nodeSource, stillValid nodeSource, removed propagation, clearFilterEntry expanded, saveFilterItems isNbtOnlySlot |
| `network/SetFilterEntryAmountPayload.java` | amount → batch+stock fields, StreamCodec.of with FriendlyByteBuf |
| `network/SetFilterEntryNbtPayload.java` | ACTION_SET_RAW, setRaw factory, add() with fallbackValue |
| `network/ServerPayloadHandler.java` | 4 new handlers, updateChannelData null direction, handleSetFilterEntryAmount batch/stock, handleSetFilterEntryNbt fallbackValue+SET_RAW, markNetworkDirty public |
| `Logisticsnetworks.java` | 4 new payload registrations |
| `client/FilterClickHandler.java` | NodeScreen filter slot detection → OpenNodeFilterPayload |
| `client/screen/FilterScreen.java` | Full detail page system (~1950 lines added) |
| `client/screen/NodeScreen.java` | Channel naming, cycleSide with null, settings tooltips, batch max per type, getSelectedChannel |
| `client/screen/ClipboardScreen.java` | keyPressed override (consume non-Esc) |
| `client/screen/ComputerScreen.java` | keyPressed final fallthrough (consume non-Esc) |
| `client/screen/MassPlacementScreen.java` | keyPressed override (consume non-Esc) |
| `client/screen/PatternSetterScreen.java` | keyPressed override with multiplierField handling |
| `integration/jei/FilterGhostIngredientHandler.java` | Detail page ghost ingredient target |
| `integration/mekanism/ChemicalTransferHelper.java` | Nullable direction in getHandler + transferBetween |
| `lang/en_us.json` | Import→Receiver, Export→Sender, "All" direction, 33 new keys (detail page, tooltips, channel name) |
| `gradle.properties` | version 1.0.0→1.1.0, open version range |
| `build.gradle.kts` | Conditional copyJar with File() existence check |

### Skipped Files
| File | Reason |
|------|--------|
| `item/DurabilityFilterItem.java` | Does not exist on 26.1 (durability filter deprecated/removed) |
| `.github/workflows/release.yml` | Already identical between branches |

## API Adaptations (1.21.1 → 26.1)

| 1.21.1 | 26.1 | Where |
|--------|------|-------|
| `ResourceLocation` | `Identifier` | New payload TYPE definitions |
| `PacketDistributor.sendToServer()` | `ClientPacketDistributor.sendToServer()` | All client screen code |
| `IItemHandler` | `ResourceHandler<ItemResource>` | TransferEngine find*Handler |
| `IFluidHandler` | `ResourceHandler<FluidResource>` | TransferEngine find*Handler |
| `IEnergyStorage` | `EnergyHandler` | TransferEngine find*Handler |
| `editBox.keyPressed(k,s,m)` | `editBox.keyPressed(ClientInput.key(k,s,m))` | Screen key handling |
| `editBox.charTyped(c,m)` | `editBox.charTyped(ClientInput.character(c,m))` | Screen char handling |
| `tag.getString(key)` | `tag.getStringOr(key, "")` | ChannelData, NodeClipboardConfig |
| `tag.getInt(key)` | `tag.getIntOr(key, 0)` | FilterItemData batch/stock |
| `tag.getBoolean(key)` | `tag.getBooleanOr(key, false)` | FilterItemData enchanted |
| `RegistryFriendlyByteBuf` in StreamCodec.of | `FriendlyByteBuf` | New payloads (? super constraint) |
| `tooltip.add()` | `tooltip.accept()` | SlotFilterItem |
| `playerInv.selected` | `playerInv.getSelectedSlot()` | FilterMenu constructors |
| `ChannelData.save(CompoundTag)` only | Dual: `save(CompoundTag)` + `save(ValueOutput)` | ChannelData null direction |
| `MultiLineEditBox(font,x,y,w,h,c)` | `MultiLineEditBox.builder(font).pos(x,y).size(w,h).build()` | FilterScreen |
| `guiGraphics.renderTooltip(font, list, x, y)` | `graphics.renderTooltip(font, list, x, y)` via wrapper | FilterScreen |

## Net Effect
- Filter detail page: Ctrl+click any filter slot to edit batch, stock, NBT rules, enchanted constraint, slot mapping, durability per-entry
- Channel naming: double-click channel tab in node screen to rename (24 char max)
- All-sides direction: null direction probes all 6 faces for capability handlers
- Batch/stock system: replaces single "amount" field with separate batch (per-tick) and stock (threshold) controls
- Import→Receiver / Export→Sender terminology
- Deprecation tooltips on SlotFilterItem
- Settings tooltips on node screen with 1s hover delay
- Key consumption: screens consume non-Esc keys to prevent game keybind leaks
