# Plugin Test Checklist

## Gold Economy
- [ ] `/pay <player> <amount>` — gold deducted from sender's inventory, received at target's feet if full
- [ ] `/pay` with insufficient gold — rejects cleanly with error message
- [ ] `/request <player> <amount>` — recipient notified in chat immediately if online
- [ ] `/requests received` — shows [Accept] / [Decline] buttons
- [ ] `/requests sent` — shows [Cancel] button
- [ ] Accept a request with sufficient gold — gold transfers, both balances update
- [ ] Accept a request with insufficient gold — request stays pending
- [ ] Cancel a request as the sender — disappears from both sides
- [ ] Accept a request when requester is offline — should cancel

## Gold Tracking & DB
- [ ] Gold scanner updates DB every 10s — `gold` field shows correct decimal (e.g. `11.89`)
- [ ] Place a gold block, wait for scan — DB value increases by 9
- [ ] Break your own gold block — DB value drops back down
- [ ] Place a chest, put gold in it, wait for scan — chest gold included in total

## Block & Chest Protection
- [ ] Break another player's chest — blocked with red message
- [ ] Open another player's chest — opens and contents are visible, but nothing can be moved
- [ ] Shift-click item from own inventory into another player's chest — blocked
- [ ] Drag item into another player's chest — blocked
- [ ] Break another player's barrel — blocked with red message
- [ ] Open another player's barrel — view-only, no interaction allowed
- [ ] Break another player's ender chest — blocked with red message
- [ ] Open any ender chest — always shows your own contents (no protection needed)
- [ ] Break another player's gold block — blocked with red message
- [ ] Break your own chest — works, chest untracked
- [ ] Break your own barrel — works, barrel untracked
- [ ] Break your own gold block — works, block untracked

## Barrel & Ender Chest Gold Tracking
- [ ] Place a barrel, put gold in it, wait for scan — barrel gold included in total
- [ ] Put gold in your ender chest, wait for scan — ender chest gold included in total
- [ ] Break your barrel — gold is removed from total on next scan

## Gold Drop Ownership
- [ ] Drop a gold ingot — another player cannot pick it up
- [ ] Drop a gold nugget — another player cannot pick it up
- [ ] Drop a gold block — another player cannot pick it up
- [ ] Drop a gold ore — another player cannot pick it up
- [ ] The player who dropped it can still pick it back up
- [ ] Dropped gold despawns normally after 5 minutes

## Gold Storage Restriction
- [ ] Try to place gold ingot into a shulker box — blocked with red message
- [ ] Try to place gold nugget into a hopper — blocked with red message
- [ ] Try to place gold block into a dropper — blocked with red message
- [ ] Try to place gold ore into a dispenser — blocked with red message
- [ ] Shift-click gold from inventory into a shulker box — blocked
- [ ] Drag gold into a shulker box — blocked
- [ ] Non-gold items can still be placed into these containers normally

## Leaderboard
- [ ] `/goldscore` — players sorted by gold, top 3 in gold/silver/bronze colours
- [ ] Sidebar scoreboard visible on right side of screen, updates every 10s

## Ghost Death State

### Entering ghost mode
- [ ] Die normally — after clicking Respawn, floating player skull appears above your head
- [ ] Skull follows you as you move around the world
- [ ] Other players can see your skull floating above you

### World restrictions while dead
- [ ] Try to break a block — blocked (no block break)
- [ ] Try to place a block — blocked
- [ ] Try to open a chest — blocked
- [ ] Try to press a button / lever — blocked
- [ ] Try to attack a mob — blocked
- [ ] Try to attack another player — blocked
- [ ] Try to drop an item — blocked
- [ ] Fall into lava or off a cliff — no damage taken

### Persistence
- [ ] Disconnect while in ghost state — log back in, skull reappears and reminder message shown
- [ ] Server restart while a player is in ghost state — skull reappears on next login, no orphan ArmorStands left in the world

### `/pay death` — revival
- [ ] Run `/pay death` while NOT dead — error message, nothing happens
- [ ] Die with 0 gold across all sources — `/pay death` revives for free with message "No gold to pay"
- [ ] Die with gold only in inventory — 50% taken from inventory, skull removed, movement and interaction restored
- [ ] Die with gold only in chests/barrels — 50% pulled from chest inventories, skull removed
- [ ] Die with placed gold blocks — blocks are broken silently (not dropped), 50% taken; if blocks overshoot debt, excess refunded to inventory
- [ ] Die with 20 gold (2 blocks + 2 ingots) — both blocks broken, 8 gold refunded to inventory, 2 ingots untouched
- [ ] Die with gold spread across blocks + chests + inventory — blocks drained first, then chests, then inventory; stops as soon as debt covered

## Misc
- [ ] Kill a chicken — Court Officer spawns, fine note drops, fine logged to `minecraft_fines`
- [ ] Player join — profile upserted in `minecraft_players`, pending requests announced in chat
