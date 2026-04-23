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

## Misc
- [ ] Kill a chicken — Court Officer spawns, fine note drops, fine logged to `minecraft_fines`
- [ ] Player join — profile upserted in `minecraft_players`, pending requests announced in chat
