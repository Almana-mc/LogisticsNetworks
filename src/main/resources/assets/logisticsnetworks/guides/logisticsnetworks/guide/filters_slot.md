---
item_ids: [logisticsnetworks:slot_filter]
navigation:
  title: Slot Filter
  parent: filters.md
  icon: logisticsnetworks:slot_filter
  position: 8
---

# Slot Filter

> Deprecated: Slot mapping is now available per-entry in the filter detail page. Ctrl+Left Click a base filter slot to access it. The standalone recipe has been removed.

Slot Filter limits inventory slot access for item transfer.

Slot range is 0 to 53.

Input format examples:

1. `0, 1, 2`
2. `4-9`
3. `0,1,4-9`

Behavior:

1. Whitelist mode allows only listed slots.
2. Blacklist mode blocks listed slots.

The same logic is applied on both ends:

1. Export source slots for extraction
2. Import target slots for insertion

This filter is used by item transfer path only.

Recipe removed. Use a base filter (Small/Medium/Big) with the filter detail page instead.

