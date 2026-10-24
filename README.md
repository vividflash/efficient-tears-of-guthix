# Efficient Tears of Guthix

Feeling inefficient at collecting tears?\
This plugin highlights the most efficient spot(s) to pick in Tears of Guthix.

## Features

Works on `GGGBBB` and `BBBGGG` worlds.

**General**

- **Reaction speed**: `0` if you click the moment the highlight moves, `1` if
  you take about a tick (0.6 seconds). At `1` the last blue stream of a burst
  shows the first blue wall a tick earlier and sticks to it. Default `0`.
- **Deprioritise green walls**: left click on a green wall walks. Default
  `on`.
- **Deprioritise Juna**: until the stream order is synced and supported,
  left click on Juna walks. Default `on`.
- **Allow BBBGGG worlds**: off treats `BBBGGG` worlds as not supported.
  Default `on`.

**Walls & Tiles**

- **Wall**: marks the best wall in **Best wall colour**, and keeps it marked
  while you stand on it. Default `on`.
- **Wall style**: how a wall with a stream is marked. `Outline` of the stream,
  or `Hull` of the stream filled in. Default `Outline`.
- **Tile**: marks the tile to stand on in front of those walls. Default `on`.
- **Second best**: marks the next best wall while you move, in **Second best
  colour**. Default `on`.
- **Mark possible walls**: walls the next blue stream can land on, in
  **Possible wall colour**. Default `on`.
- **Mark bad walls**: walls no blue stream can land on next, or a green one
  can, in **Bad wall colour**. Default `off`.
- **Stream countdown**: ticks until your wall's blue stream moves. Default
  `on`.
- **Dry wall countdown**: in red, ticks until the wait at a dry wall ends.
  Default `off`.

**Interface**

- **Info panel**: stream order, session ticks left, and at Juna when to
  enter. Default `on`.
- **Separate timer box**: the entry timing and ticks left in a box of their
  own. Default `off`.
- **XP skill**: at Juna, the skill that gets the XP. Default `on`.
- **Refresh XP skill**: picks the skill again after each XP gain in the cave,
  otherwise only when Juna comes into view. Default `off`.
- **Tear change**: tears gained or lost, beside the tear counter. Default
  `on`.
- **Ticks left**: `Overlay` (info panel or timer box), `Interface` (above
  the time bar) or `Both`. Default `Interface`.
- **Login reminder**: chat message on login naming the skill that gets the XP
  while a run is open. Default `off`.
