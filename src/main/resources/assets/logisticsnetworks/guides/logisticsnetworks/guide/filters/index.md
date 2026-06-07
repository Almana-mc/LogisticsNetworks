---
item_ids: [logisticsnetworks:small_filter, logisticsnetworks:medium_filter, logisticsnetworks:big_filter, logisticsnetworks:mod_filter, logisticsnetworks:name_filter]
navigation:
  title: Filters
  position: 4
---

# Filters

Filters are virtual channel rules that decide exactly which resources a channel is allowed to move. Open them from the 3x2 filter grid on the right side of the node screen; see [Filters & Upgrades](../nodes/filters-upgrades.md).

Without any filters the channel transfers everything that matches its Type. Add a filter and only resources that pass the filter's rules get through.

Filter slots are **per-channel**. Every channel on a node keeps its own independent set of 6 filter buttons, so one node can run 6 totally different filter configurations at once.

## Sender vs Receiver

Filters live on both Senders and Receivers, but they do different jobs depending on which side of the transfer you put them on:

- **Sender (exporter)**: the filter decides **what the Sender pulls out** of the block it is attached to. Only resources that pass the filter are extracted and offered to the network.
- **Receiver (importer)**: the filter decides **what the Receiver accepts** into the block it is attached to. The network may be full of resources, but the Receiver only takes the ones that pass its filter.

Most setups put filters on both sides. A Sender-side filter decides what leaves the source; a Receiver-side filter decides what lands at the destination. A common pattern is one Sender pulling a wide range of items from a dump chest, and several Receivers each filtering a narrow subset.

Whitelist / Blacklist, Match Any / Match All, and every per-entry rule all honor this Sender-vs-Receiver split. Stock in particular swaps meaning between the two sides, since "keep a reserve" and "cap the destination" are not the same thing.

## Whitelist vs Blacklist

Every filter has two modes:

- **Whitelist**: the filter is a list of things the channel is **allowed** to move. Anything not on the list is blocked.
- **Blacklist**: the filter is a list of things the channel is **not** allowed to move. Everything else gets through.

Open a virtual filter from the node filter grid to flip the mode.

## Match Any vs Match All

When a channel has more than one filter in its grid, the **Any / All** button at the top of the Filters panel decides how they combine:

- **Match Any**: a resource passes if **at least one** filter accepts it.
- **Match All**: a resource passes only if **every** filter accepts it.

For a full walkthrough, see [Filters & Upgrades](../nodes/filters-upgrades.md#filters).

## Filter Types

### Small, Medium, and Big

Small, Medium, and Big filters all use the same exact-match behavior and the same **45-slot** entry grid.

- Match exact item ids and fluid ids.
- Put any item or fluid bucket into an entry slot to add it to the list.
- Remove the entry to take it off the list.
- Empty entry slots are ignored.

Each entry can also open a Detail page for tag matching, batch overrides, stock thresholds, NBT rules, and attached-inventory slot restrictions.

### Mod

Mod filters match every item or fluid from one configured mod id.

- Set the mod id, such as `minecraft`, `create`, `mekanism`, or `ae2`.
- One Mod filter matches one mod.
- To match several mods, put multiple Mod filters in the same channel and use **Match Any**.

### Regex

Regex filters match by display name or tooltip text using Java regex syntax.

- Set the regex pattern.
- Choose the scope: **Name**, **Tooltip**, or **Both**.
- Use anchors such as `^` and `$` when you want stricter matches.

Common examples:

| Pattern | Matches |
|---------|---------|
| `Iron` | Anything containing "Iron" |
| `^Iron` | Names starting with "Iron" |
| `Ingot$` | Names ending with "Ingot" |
| `^Iron Ingot$` | Exactly "Iron Ingot" |
| `(?i)iron` | Case-insensitive "iron" |
| `Iron\|Gold` | Names containing "Iron" or "Gold" |
| `Silk Touch` | Tooltip text containing "Silk Touch" |

Regex filters run against every candidate resource the channel checks, so keep patterns simple on high-traffic channels.

### Slot

Slot filters restrict which slot indices on the attached block the channel can read or write.

- Use comma-separated slots and ranges, such as `0`, `0-8`, or `0-3,5`.
- Valid slot indices are `0` through `53`.
- On Senders, only the listed source slots are extracted from.
- On Receivers, only the listed destination slots are inserted into.

## Entry Details

Every slot in a Small, Medium, or Big filter's main grid is more than a single-item check. Open the Detail page for a filled entry to configure these per-entry rules:

- **Item or #tag**: match an exact id like `minecraft:iron_ingot`, or a tag like `#c:ores`.
- **Batch**: override how many of this entry move per transfer on the Sender side.
- **Stock**: reserve items on Senders, or cap destination stock on Receivers.
- **NBT**: add up to 6 active NBT/component rules, or paste raw SNBT for advanced cases.
- **Slots**: restrict this entry to specific attached-inventory slot indices.

Leave Batch or Stock at `0` to fall back to the channel settings or disable the threshold.

## Copying Filters

Filter configurations are copied with node copy/paste, labelled-node sync, and `.lnet` save/load workflows. To clear a virtual filter, clear its entries inside the filter menu.
