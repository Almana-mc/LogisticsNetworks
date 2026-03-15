---
navigation:
  title: Channels
  parent: index.md
  position: 3
---

# Channels

Each node has 9 channels. A channel defines one transfer operation — what type of resource to move, which direction, and how much.

## Channel Settings

- **Status** — Enable or disable the channel
- **Mode** — Import (push into this block) or Export (pull from this block)
- **Type** — Item, Fluid, Energy, Chemical (requires Mekanism upgrade), or Source (requires Ars upgrade)
- **Side** — Which face of the block to interact with
- **Redstone** — Always On, Always Off, High Signal, or Low Signal
- **Distribution** — How exports distribute among targets: Priority, Nearest First, Farthest First, Round Robin, or Recipe RR
- **Filter Mode** — Match Any or Match All (when multiple filters are present)
- **Priority** — Higher priority channels are served first
- **Batch** — How many items/mB/RF to transfer per operation
- **Delay** — Tick delay between operations

## How Transfers Work

The transfer engine processes each network sequentially. Export channels are processed in order and pull resources from their attached block, pushing them to matching import channels on the same network. When an export channel finds multiple matching imports, priority determines the order in which imports receive resources. Each operation respects the configured batch size and delay.
