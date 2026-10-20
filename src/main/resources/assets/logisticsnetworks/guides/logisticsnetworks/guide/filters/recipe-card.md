---
item_ids: [logisticsnetworks:recipe_card]
navigation:
  title: Recipe Card
  parent: filters/index.md
  position: 1
---

# Recipe Card

A Recipe Card makes a channel send whole recipe sets instead of loose resources. A card can mix items, fluids and Mekanism chemicals. With a card for 2 iron + 1000 mB water, receivers only ever get 2 iron and 1000 mB water together, never the iron on its own.

## Writing a recipe

Craft paper, redstone and an iron nugget for a blank card. Right-click a card to open it. With JEI or EMI, open any recipe and press **+** to copy its ingredients onto the card, fluids and chemicals included. You can also click a cell while holding an item, type the amount, and press **#** to switch between the exact item and its tags. Hold **Modifier Key 1** (Shift by default) while clicking with a bucket or tank to add what's inside instead of the container itself. Fluid and chemical amounts are in mB. Right-click a cell to remove it. A card holds up to 12 different ingredients.

Craft a written card on its own to wipe it, or together with blank cards to copy it onto them.

## Using it in a filter

A card is an entry in a filter, like any other entry, and it works in item, fluid and chemical filters alike. Hold it and click a cell in the filter, or click a node's filter slot to add it to that slot's filter. The filter gets a copy and you keep the card. The cell cycles through the card's ingredients, and its name shows what the recipe makes.

Hold **Modifier Key 2** (Ctrl by default) and click the cell to edit that copy. Close the editor to go back to the filter. Click the cell with an empty hand to clear it. Cards work in whitelist and blacklist filters, in the wrench clipboard, and they copy with labels. A card in an import channel's filter does nothing. Cards can't be edited from the computer graph or from a filter item held in your hand.

## Sending sets

A channel with a card sends the whole set, whatever its own type is: items, fluids and chemicals together. The receivers are the import channels with the same number, whether they are item, fluid or chemical channels. Each receiver's own filter still applies to its own resource type. Chemicals need a Mekanism Chemical Upgrade on the sending node.

On a channel with a card, **Batch** counts whole sets per operation (at least one). The node's upgrade limits still cap each resource, so a set bigger than one operation's limit goes out one set at a time. The distribution mode decides who gets them:

- **Priority / Nearest / Farthest**: fill the first receiver, then the next.
- **Priority Robin**: one receiver per operation, rotating.
- **Equal Distribution**: sets are split evenly; leftovers go to the first receivers.

If a receiver can't fit a whole set, it gets nothing. Every ingredient has to go in through the one side the receiving node is on, so machines that take a second input on a different side can't be fed a multi-ingredient card from one node.

## Blocking

Turn on **Blocking** to send only to receivers that hold none of this channel's recipe ingredients, in any slot or tank. Machines that take several recipes then never get two recipes mixed.

## Mixing with normal filters

A filter can hold cards and normal entries together. Recipe sets go first, then the normal entries move loose resources of the channel's type. The two have separate budgets: Batch sets for the cards, and Batch in the channel's own unit for the normal entries. A channel whose filters hold only cards sends nothing else.
