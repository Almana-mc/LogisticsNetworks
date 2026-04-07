---
item_ids: [logisticsnetworks:name_filter]
navigation:
  title: Regex Filter
  parent: filters.md
  icon: logisticsnetworks:name_filter
  position: 4
---

# Regex Filter

Regex Filter matches by regex pattern (case-insensitive) on display names and tooltips.

Example:

If you set `.*stone.*`, it matches any target whose name contains "stone". The pattern uses Java regex syntax and is case-insensitive.

Match scope can be set to:

1. Name: matches against display name only.
2. Tooltip: matches against tooltip lines only.
3. Both: matches against name and tooltip lines.

Target type can be:

1. Items
2. Fluids
3. Chemicals

Chemical name matching uses Mekanism text when available, then falls back to chemical id text.

<RecipeFor id="logisticsnetworks:name_filter" />

