---
item_ids: [logisticsnetworks:logistics_node]
navigation:
  title: Nodes
  parent: index.md
  icon: logisticsnetworks:logistics_node
  position: 1
---

# Logistics Nodes

Nodes are the building blocks of every logistics network. Each node attaches to a block and provides 9 channels for transferring resources.

## Placing Nodes

Hold a Logistics Node item and right-click any block that has an inventory, fluid tank, or energy storage. The node entity will attach to that block face.

Nodes can only be placed on blocks that are not blacklisted and have at least one storage capability.

## Node Configuration

Right-click a placed node with a Wrench to open its settings screen.

From here you can:

- Assign the node to a network or create a new one
- Configure each of the 9 channels
- Insert filters and upgrades
- Set a label for organization
- Toggle node visibility (visible/hidden when holding wrench)

## Labels

Labels are a powerful way to keep groups of nodes in sync. When multiple nodes share the same label within a network, their channel configurations stay linked — changing one updates them all.

### Setting a Label

1. Open the node configuration screen
2. Click the label button above the settings panel
3. Type a new label (up to 48 characters) or select an existing one from the dropdown
4. Press Enter to confirm

The label picker lists all labels already in use on the same network, so you can quickly assign a node to an existing group.

### How Label Sync Works

When you assign a label to a node that matches an existing labeled node in the same network, all 9 channels are immediately copied from that existing node. This means the new node inherits the full configuration — channel modes, types, filters, batch sizes, delays, and everything else.

After that, any channel change you make on any node in the label group is propagated to every other node with the same label. This includes changes to channel settings, filter items, and filter configurations. Each node's upgrades are respected independently, so batch sizes are clamped to what each node's upgrades allow.

This makes it easy to configure many identical nodes at once. For example, if you have 20 furnaces that all need the same import/export setup, label them all "furnace" and configure one — the rest follow automatically.

### Labels in the Computer

The Computer's Node Table groups nodes by label, making it easy to see which nodes belong together. You can also highlight all nodes with a given label at once from the Node Table.

## Visibility

Nodes can be toggled between visible and hidden. Hidden nodes do not render when holding a wrench, reducing visual clutter in large setups. Visibility can be toggled from the node screen or from the Computer's Node Table.

## Removing Nodes

Shift-right-click a node with the wrench to remove it. Breaking the block a node is attached to also removes the node (dropping the node item if configured).
