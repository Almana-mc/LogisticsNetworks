---
item_ids: [logisticsnetworks:computer]
navigation:
  title: Computer
  parent: index.md
  icon: logisticsnetworks:computer
  position: 6
---

# Computer

The Computer is a placeable block that provides a terminal interface for monitoring and managing your logistics networks.

## Opening the Computer

Right-click the Computer block to open the terminal. The Computer automatically detects a Wrench in your inventory and loads it into the drive bay. Your networks are listed in the Network Directory on the left.

## Network Directory

The left panel lists all networks you own. Click a network to mount it and view its details. Scroll to navigate if you have many networks. The panel shows each network's name and node count.

## Subsystems

After selecting a network, two subsystems are available:

### I/O Monitor

The I/O Monitor shows all active channels across the selected network, aggregated by channel index. Each entry displays the channel number, node count, and resource type.

Click a channel entry to open its throughput graph. The graph shows a live timeline of transfer flow (items/s, mB/s, RF/s, etc.) updated every second. The telemetry history holds 120 data points.

Use the type tabs (Items, Fluids, Energy, Chemicals, Source) to filter which channel types are displayed.

### Node Table

The Node Table lists all nodes in the selected network, grouped by label. For each node you can see:

- The block type the node is attached to
- Its coordinates and dimension
- Highlight and settings buttons

**Highlight** — Click the highlight lamp icon next to a node to toggle its in-world glow outline, making it easy to locate. You can also highlight all nodes sharing a label at once.

**Settings** — Click the settings icon to remotely open that node's configuration screen.

**Visibility** — Use the Show/Hide buttons at the top to bulk-toggle render visibility for all nodes in the network.
