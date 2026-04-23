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
- [ ] Open another player's chest (right-click) — blocked with red message
- [ ] Break another player's gold block — blocked with red message
- [ ] Break your own chest — works, chest untracked
- [ ] Break your own gold block — works, block untracked

## Leaderboard
- [ ] `/goldscore` — players sorted by gold, top 3 in gold/silver/bronze colours
- [ ] Sidebar scoreboard visible on right side of screen, updates every 10s

## Misc
- [ ] Kill a chicken — Court Officer spawns, fine note drops, fine logged to `minecraft_fines`
- [ ] Player join — profile upserted in `minecraft_players`, pending requests announced in chat
