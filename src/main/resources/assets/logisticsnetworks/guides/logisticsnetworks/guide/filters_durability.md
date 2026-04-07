---
item_ids: [logisticsnetworks:durability_filter]
navigation:
  title: Durability Filter
  parent: filters.md
  icon: logisticsnetworks:durability_filter
  position: 7
---

# Durability Filter

> Deprecated: Durability filtering is now available per-entry in the filter detail page. Ctrl+Left Click a base filter slot to access it. The standalone recipe has been removed.

Durability Filter checks remaining durability on damageable items.

You can set:

1. Operator: less or equal, equal, greater or equal
2. Value: from 0 to 3000

Remaining durability is:

`max damage minus current damage`

Current behavior in transfer logic:

1. It is checked for item matching.
2. It requires a positive match.
3. Blacklist toggle on this filter does not invert the result.

It does not affect fluid, chemical, energy, or source transfers.

Recipe removed. Use a base filter (Small/Medium/Big) with the filter detail page instead.

