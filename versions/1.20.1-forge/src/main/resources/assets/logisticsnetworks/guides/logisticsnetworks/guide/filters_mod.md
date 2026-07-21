---
item_ids: [logisticsnetworks:mod_filter]
navigation:
  title: Mod Filter
  parent: filters.md
  icon: logisticsnetworks:mod_filter
  position: 3
---

# Mod Filter

Mod Filter matches items, fluids, or chemicals by their mod namespace. You can type a namespace or place an item in the extractor slot and select from the picker.

The match is exact on the namespace portion of the item id (the part before the colon).

## Examples

1. `minecraft` matches all vanilla items (e.g. minecraft:stone, minecraft:diamond)
2. `mekanism` matches all Mekanism items and chemicals
3. `create` matches all Create mod items and fluids
4. `thermal` matches all Thermal Series items
5. `ae2` matches all Applied Energistics 2 items

Typing `minecraft:stone` will be normalized to `minecraft`.

<RecipeFor id="logisticsnetworks:mod_filter" />

