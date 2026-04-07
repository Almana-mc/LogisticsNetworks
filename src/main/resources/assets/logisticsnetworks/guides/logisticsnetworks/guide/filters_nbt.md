---
item_ids: [logisticsnetworks:nbt_filter]
navigation:
  title: NBT Filter
  parent: filters.md
  icon: logisticsnetworks:nbt_filter
  position: 5
---

# NBT Filter

> Deprecated: NBT filtering is now available per-entry in the filter detail page with operator support (=, !=, >=, <=) and raw SNBT mode. Ctrl+Left Click a base filter slot to access it.

NBT Filter stores one selected path and one exact value.

Setup flow:

1. Put an item or fluid container in extractor slot.
2. Pick a path from the detected list.
3. The value at that path is stored on the filter.

Match is exact equality on the selected value.

Target types in UI:

1. Items
2. Fluids
3. Chemicals

Current code supports matching for Items and Fluids.
There is no chemical NBT matching implementation right now.

<RecipeFor id="logisticsnetworks:nbt_filter" />

