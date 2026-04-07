---
navigation:
  title: Channels
  parent: index.md
  position: 4
---

# Channels

Each node has 9 channels, indexed from 0 to 8.

A transfer path is built like this:

1. Source node channel in Sender mode
2. Target node channel with same channel index
3. Target channel in Receiver mode
4. Same resource type on both channels

So channel index and resource type must match.

Resource types in code:

1. Item
2. Fluid
3. Energy
4. Chemical
5. Source

Chemical type is only selectable when Mekanism is loaded and that node has Mekanism Chemical Upgrade.

Source type is only selectable when Ars Nouveau is loaded and that node has Ars Source Upgrade.

## Redstone Rules

Sender channels use the redstone mode directly:

1. Always On: runs without needing redstone power.
2. Always Off: never runs.
3. High Signal: runs only when redstone signal is greater than 0.
4. Low Signal: runs only when redstone signal is 0.

Receiver channels are cached only when redstone mode is Always On.

## Distribution Modes

Distribution applies on Sender channels:

1. Priority: targets with higher priority value are tried first.
2. Nearest First: closest target node is tried first.
3. Farthest First: farthest target node is tried first.
4. Round Robin: targets are rotated after successful transfer.
5. Recipe Robin: round robin respecting recipe ratios.

Round Robin and Recipe Robin rotate targets after successful moves. Recipe Robin distributes items proportionally to match recipe ratios across targets.

## Filter Mode

Each channel has filter mode:

1. Any: one whitelist filter match is enough.
2. All: every whitelist filter must match.

This controls whitelist checks. Blacklist hits always block.

Current transfer code checks filters for Item, Fluid, and Chemical channels.
Energy and Source channels do not read filter slots for matching.

## Channel Naming

Each channel can be given a custom name. This is a label only and does not affect transfer logic.
