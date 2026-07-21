---
item_ids: [logisticsnetworks:name_filter]
navigation:
  title: Regex Filter
  parent: filters.md
  icon: logisticsnetworks:name_filter
  position: 4
---

# Regex Filter

Regex Filter matches items, fluids, or chemicals by name using Java regex patterns. All matching is case-insensitive and partial (the pattern does not need to match the full name).

Match scope:

1. Name: matches against display name only
2. Tooltip: matches against tooltip lines only
3. Both: matches against name and tooltip lines

## Examples

1. `stone` matches anything containing "stone" (e.g. Stone, Cobblestone, Stonecutter)
2. `^Iron` matches names starting with "Iron" (e.g. Iron Ingot, Iron Sword)
3. `Sword$` matches names ending with "Sword" (e.g. Diamond Sword, Netherite Sword)
4. `^Diamond .+` matches names starting with "Diamond " followed by anything
5. `Potion|Arrow` matches anything containing "Potion" or "Arrow"
6. `Deepslate.*Ore` matches names containing "Deepslate" followed by "Ore" (e.g. Deepslate Iron Ore)
7. `^(?!Golden).*Helmet` matches any Helmet except Golden Helmet

<RecipeFor id="logisticsnetworks:name_filter" />

