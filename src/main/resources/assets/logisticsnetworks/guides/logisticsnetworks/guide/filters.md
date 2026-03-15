---
navigation:
  title: Filters
  parent: index.md
  position: 4
---

# Filters

Filters control exactly which resources a channel will transfer. Each channel has dedicated filter slots where filter items can be placed.

## Filter Types

- **Small / Medium / Big Filter** — Match specific items or fluids by exact type. Differ in number of entry slots (varies by size).
- **Tag Filter** — Match any item or fluid that belongs to a specific tag (e.g. `c:ores`).
- **Mod Filter** — Match all items or fluids from a specific mod.
- **Amount Filter** — Set a threshold: exports keep a reserve, imports cap at a limit.
- **Durability Filter** — Match items by remaining durability percentage.
- **NBT Filter** — Match items by specific NBT data paths.
- **Slot Filter** — Restrict which inventory slots a channel can access (whitelist or blacklist).
- **Regex Filter** — Match items by name or tooltip using a regular expression.

## Filter Modes

Each filter can be set to Whitelist or Blacklist mode. Whitelist only transfers matching resources, Blacklist transfers everything except matches.

## Copying Filters

Filters can be copied between channels using the crafting grid. Place a configured filter with an empty filter of the same type to copy the configuration. Place a configured filter alone to clear it.
