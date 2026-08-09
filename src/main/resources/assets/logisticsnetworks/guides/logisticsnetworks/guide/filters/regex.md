---
item_ids: [logisticsnetworks:name_filter]
navigation:
  title: Regex Filter
  parent: filters/index.md
  icon: logisticsnetworks:name_filter
  position: 5
---

# Regex Filter

Matches items by their **display name**, using a regular expression (regex). Powerful but easy to misuse — read the warnings at the bottom before putting one on a high-traffic channel.

## What It Matches

Any item whose display name matches the regex pattern you set. The match is checked against the name the item would show in-game, not its registry id. Tooltip lines are not matched — they only exist on the client, so a server cannot read them.

## Config

Right-click the filter in-hand to open the menu. You will see:

- **Regex pattern** — the regular expression to test against the item's name. Standard Java regex syntax.
- **Whitelist / Blacklist** toggle.

The item's own tooltip shows the pattern and the mode at a glance.

## Whitelist vs Blacklist

- **Whitelist** — only items whose text matches the regex are allowed through.
- **Blacklist** — items whose text matches are blocked; everything else passes.

## Use Cases

- Match any item whose name starts with "Iron" → pattern `^Iron`.
- Move every kind of ingot onto one channel → pattern `Ingot$`.
- Block anything named "Debug" from being imported into your storage → pattern `Debug`, mode Blacklist.

## Regex Examples

A quick cheat sheet for common patterns.

| Pattern | Matches | Example items |
|---------|---------|---------------|
| `Iron` | anything containing "Iron" | Iron Ingot, Iron Pickaxe, Iron Door |
| `^Iron` | names **starting** with "Iron" | Iron Ingot, Iron Pickaxe |
| `Ingot$` | names **ending** with "Ingot" | Iron Ingot, Gold Ingot, Copper Ingot |
| `^Iron Ingot$` | exactly "Iron Ingot" — no Iron Block, no Iron Pickaxe | Iron Ingot only |
| `(?i)iron` | case-insensitive match for "iron" | Iron Ingot, iron ingot, IRON INGOT |
| `Iron\|Gold` | names containing "Iron" **or** "Gold" | Iron Ingot, Gold Nugget, Gold Block |
| `.*Axe$` | anything ending in "Axe" | Iron Axe, Diamond Axe, Netherite Axe |
| `^(?!.*Netherite).*Pickaxe$` | any Pickaxe that is **not** Netherite | Iron / Gold / Diamond Pickaxe |
| `\d+` | anything containing a digit | items with numbers in the name |
| `Enchanted Book` | exact phrase appears anywhere | any enchanted book |

### Syntax Quick Reference

- `.` — any single character
- `^` — start of the text being tested
- `$` — end of the text being tested
- `*` — zero or more of the previous thing
- `+` — one or more
- `?` — zero or one (also marks a group as non-capturing when used like `(?:...)`)
- `\d` — digit, `\w` — word character, `\s` — whitespace
- `[abc]` — any one of a, b, c
- `[^abc]` — anything **not** a, b, or c
- `a|b` — a or b
- `(?i)` — case-insensitive mode flag (put at the start of the pattern)
- Escape special characters with `\`: `\.`, `\(`, `\[`, `\\`

### Learning & Testing Regex

Tools to build and test patterns before pasting them into the filter:

- **[regex101.com](https://regex101.com/)** — live regex tester with explanation pane. Set the flavor to **Java 8** in the sidebar so it matches what this filter uses.
- **[regexr.com](https://regexr.com/)** — another live tester, good for beginners. Uses JavaScript flavor but most basic patterns behave the same.
- **[Oracle Java Pattern docs](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html)** — the authoritative reference for the exact regex syntax this filter uses (Java 21).
- **[regex-cheatsheet by Rex Egg](https://www.rexegg.com/regex-quickstart.html)** — printable cheat sheet of common patterns.

## Regex Warnings

- **Cost** — every resource the channel tries to move runs through the regex. On a Sender with a big batch size and lots of matching items, that is a lot of regex evaluations every tick. Keep patterns simple and anchored when you can (`^`, `$`, no greedy `.*` where not needed).
- **Display name only** — some items translate differently depending on language. A regex tuned for English may not match the same item on a non-English client.
- **Escape special characters** — if you want to match a literal `.`, `(`, or `[`, escape it with `\`.

## Crafting Recipe

<RecipeFor id="logisticsnetworks:name_filter" />
