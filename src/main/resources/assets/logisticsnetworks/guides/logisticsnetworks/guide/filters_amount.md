---
item_ids: [logisticsnetworks:amount_filter]
navigation:
  title: Amount Filter
  parent: filters.md
  icon: logisticsnetworks:amount_filter
  position: 6
---

# Amount Filter

> Amount filtering is now also available per-entry in the filter detail page. Ctrl+Left Click a base filter slot to access it.

Amount Filter sets a threshold value.
Default value is 64.

How threshold is used:

1. On Export side it keeps stock in source.
2. On Import side it caps stock in destination.

For items:

1. Export allows moving only amount above threshold.
2. Import allows moving only amount below threshold.

For fluids:

1. Export keeps fluid reserve above threshold.
2. Import stops fill at threshold.

When multiple Amount Filters exist on a channel:

1. Export side uses the highest threshold.
2. Import side uses the lowest threshold.

Target type can be Items, Fluids, or Chemicals.
Current transfer code applies threshold logic to Items and Fluids.

<RecipeFor id="logisticsnetworks:amount_filter" />

