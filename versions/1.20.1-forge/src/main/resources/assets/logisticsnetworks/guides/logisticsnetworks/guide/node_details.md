---
navigation:
  title: Node Details
  parent: index.md
  position: 5
---

# Node Details

This page maps every setting in the node GUI.

Top controls on the channel page:

1. Network name label
2. Visibility toggle for node render
3. Change button to go back to network selection
4. Channel tabs from 0 to 8

Rows in the left settings panel:

1. Status
2. Mode
3. Type
4. Side
5. Redstone
6. Distribution
7. Priority
8. Batch
9. Delay
10. Name

## What Each Row Does

1. Status toggles channel enabled and disabled.
2. Mode cycles Receiver and Sender.
3. Type cycles Item, Fluid, Energy, Chemical, and Source with upgrade checks.
4. Side sets the capability side for extraction or insertion.
5. Redstone sets run condition.
6. Distribution picks target ordering for sender.
7. Priority is used by Priority distribution mode.
8. Batch sets max amount per operation.
9. Delay sets ticks between runs.
10. Name sets a custom label for the channel.

## Limits and Special Cases

1. Priority is clamped from minus 99 to plus 99.
2. Batch is clamped to the current upgrade tier cap.
3. Delay has a minimum from upgrade tier config.
4. Energy type forces delay to 1 tick.
5. In Receiver mode, Distribution, Batch, and Delay rows are disabled in UI.

Upgrade slots are shared for the whole node, not per channel.

## Useful Input Shortcuts

1. Left click cycles forward. Right click cycles backward.
2. Hold Shift for bigger numeric step changes.
3. Double click Priority, Batch, or Delay to type a number.
4. Hold Alt and click numeric rows to jump to min or max.

## Filter and Upgrade Slots

1. Each channel has a 3 by 3 filter grid.
2. The node has one 2 by 2 upgrade grid.

The filter mode button near the filter label toggles Any and All for that channel.

Network selection page has:

1. Network name input for new networks
2. Create Network button
3. Existing network list with scroll
4. Click entry to join that network
