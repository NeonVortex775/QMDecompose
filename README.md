# QMDecompose

A salvage station for Spigot 1.12.2 built on DragonCore custom screens. Players drop items into
the station slots, press the button, and every item that matches a table is consumed and replaced by
that table's outputs. Items that match nothing go back to the inventory, so a wrong click never
costs anything.

## Requirements

| Plugin | Needed | Why |
| --- | --- | --- |
| DragonCore 2.6.x | yes | the whole interface is a DragonCore screen |
| PlaceholderAPI | yes | the button state is driven by `%qmfx_state%` |
| NBTAPI | no | only if a table matches on NBT values |

Written against Paper 1.12.2 and DragonCore 2.6.2.9. Java 8.

## Install

1. Put the jar into `plugins/`.
2. Start the server once. `plugins/QingMingDecompose/config.yml` and `tables/` are generated.
3. Copy `dragoncore/青冥分解台.yml` into `plugins/DragonCore/Gui/`.

   DragonCore resolves a screen by **file name**, not by the `match` field inside the file. The name
   in `config.yml` under `gui.name` has to match the file exactly, otherwise `/qmfx` opens nothing
   and nothing is logged on the server side.
4. Register the slot identifiers listed in `config.yml` (`分解输入1` … `分解输入12`) in
   `plugins/DragonCore/SlotConfig.yml`. Slots that are not registered still render, but clicks on
   them never reach the server.
5. Put the textures in the client pack: `resourcepacks/DragonCore/分解/*.png`.
6. Restart the server. DragonCore reloads GUI files on restart only.

## Commands

| Command | Does | Permission |
| --- | --- | --- |
| `/qmfx` | open the station | `qmfx.use` (everyone) |
| `/qmfx open <player>` | open it for someone else | `qmfx.open` |
| `/qmfx reload` | reload config and tables | `qmfx.admin` |
| `/qmfx list` | list the loaded tables | `qmfx.admin` |
| `/qmfx info` | which table the item in hand matches | - |
| `/qmfx take` | return the slot contents to the inventory | - |

Aliases: `/fenjie`, `/qmf`.

## Tables

`tables/*.yml`, one table per file. A file can also hold several tables by making the root a set of
sections; the id then becomes `file.section`.

```yaml
id: linggen
display: "&b灵根分解"
priority: 10          # higher wins when several tables match
consume: 1            # items eaten per decompose
max-times: 64         # upper bound for a single click

match:                # all conditions have to pass
  material: [DIAMOND, EMERALD]
  name: ["&b灵根*"]           # * is a wildcard, no * means exact
  name-contains: ["灵根"]
  lore: ["&7可分解"]           # one whole lore line
  lore-contains: ["可分解"]
  nbt:
    NeigeItems:
      id: "yaodan*"           # exact, * or one of >= <= > < !=

outputs:
  - item:
      material: DIAMOND
      amount: 3
      name: "&b灵石"
      lore: ["&7分解所得"]
      glow: true
      data: 0
      nbt: { qm_type: lingshi }
  - command: "ni giveslient %player% 灵石 2"
    chance: 60                # 1-100, default 100
```

Command placeholders: `%player%`, `%uuid%`, `%world%`, `%x%`, `%y%`, `%z%`. `commands:` also
accepts a list if an output should run several lines.

`match` conditions are AND-ed. The first table that matches wins, ordered by `priority` and then by
file name. A single material list entry can carry a data value (`WOOL:14`).

## What happens on a click

1. All twelve slots are read from the client cache.
2. A slot whose item matches a table loses `consume` items and gets the outputs, repeated as far as
   `max-times` and `settings.max-times-per-slot` allow.
3. A slot that matches nothing is returned to the inventory, or dropped at the player's feet if the
   inventory is full.
4. Closing the screen, pressing Esc or logging out returns everything that is still in the slots.

The button turns grey while no slot holds anything usable; that state comes from `%qmfx_state%`.

## Filling the slots without dragging

Shift + left or right click on an item in the player inventory moves the whole stack into the slots.
Existing stacks of the same item are topped up first, empty slots only get used once those are full,
so a slot holding 10 of the item is filled to 64 before a free slot is claimed. Whatever does not fit
stays where it was - three free slots take 192 items, the rest is left in the inventory untouched.

Nothing is moved when the item has no table, or when there is no room left; both cases send the
message from `messages.shift-no-match` / `messages.shift-full` and play `sounds.fail`. A successful
insert plays `sounds.insert`.

Two settings control it:

| Key | Default | Meaning |
| --- | --- | --- |
| `settings.shift-insert` | `true` | turn the shortcut off entirely |
| `settings.shift-insert-match-only` | `true` | only items that have a table may be inserted |
| `settings.gui-alive-ms` | `3000` | how long the screen counts as open without a heartbeat |

The screen is considered open while the `Functions.open` block keeps polling the placeholders; the
timer starts when the GUI is opened and stops on `/qmfx close`, death or logout. Shift click does
nothing when the station is not open, so the shortcut cannot be used from a normal chest.

## Placeholders

| Placeholder | Value |
| --- | --- |
| `%qmfx_state%` | `1` when something can be decomposed, `0` otherwise |
| `%qmfx_msg%` | the status line at the bottom of the screen |

Both are updated every 250 ms by the `Functions.open` block of the GUI file. `settings.placeholder-cache-ms`
controls the cache behind them.

## Notes

- `settings.close-return` and `quit-return` decide whether slot contents are handed back.
- `consume` and `max-times` interact: a stack of 64 with `consume: 1, max-times: 64` yields 64 rolls
  of the output list.
- NBT matching silently never matches when NBTAPI is missing, which is easier to debug than a crash
  but does hide a missing dependency.
- Adding or removing slots means editing three places: the GUI file, `config.yml` and
  `SlotConfig.yml`.

## Build

No Maven, no Gradle.

```bash
mkdir -p lib
# spigot-api.jar, dragoncore.jar and placeholderapi.jar (NBTAPI is runtime only)
bash build.sh
# or: MC_SERVER=/path/to/server bash build.sh install
```

The output lands in `build/QingMingDecompose.jar`.
