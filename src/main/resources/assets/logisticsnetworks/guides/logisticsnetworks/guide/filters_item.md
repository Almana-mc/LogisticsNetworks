---
item_ids: [logisticsnetworks:small_filter, logisticsnetworks:medium_filter, logisticsnetworks:big_filter]
navigation:
  title: Item Filters
  parent: filters.md
  icon: logisticsnetworks:small_filter
  position: 1
---

# Item Filters

These are the general slot based filters:

1. Small Filter with 9 entries
2. Medium Filter with 18 entries
3. Big Filter with 27 entries

Each entry can store one target:

1. Item
2. Fluid
3. Chemical

Target type is selected in the filter GUI.

Matching details:

1. Item entries match by item id.
2. Fluid entries match by fluid with components.
3. Chemical entries match by exact chemical id.

Duplicate entries are blocked by the menu logic.

## Filter Detail Page

Ctrl+Left Click on any filter slot to open the filter detail page. Per-entry settings:

1. Amount threshold
2. NBT matching with operator support (=, !=, >=, <=) and raw SNBT mode
3. Durability check
4. Slot mapping (e.g. 0-8,13)
5. Enchanted flag

Remove the item from an entry to create an nbtOnly filter that matches any item with the configured NBT, durability, or enchanted settings.

JEI ghost ingredients are supported on the filter detail page.

<RecipeFor id="logisticsnetworks:small_filter" />
<RecipeFor id="logisticsnetworks:medium_filter" />
<RecipeFor id="logisticsnetworks:big_filter" />

