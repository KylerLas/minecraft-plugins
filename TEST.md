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
- [ ] Remove a gold block by non-plugin means (e.g. world edit) — next scan prunes stale tracker entry, value corrects itself

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

## Bank / Market
- [ ] `/spawnteller` (OP) — Bank Teller NPC spawns at your location
- [ ] Stand near teller holding a listed item — action bar shows sell preview with current price and % change
- [ ] Stand near teller holding an unlisted item — no sell preview shown
- [ ] Right-click teller — sells held item stack, gold added to inventory
- [ ] Sell same item repeatedly — price depresses, % shown in red when heavily sold
- [ ] Wait for recovery interval — price nudges back toward base
- [ ] `/price` holding a listed item — shows qty, current payout, and % change from base
- [ ] `/price` holding an unlisted item — "cannot be sold at the bank"
- [ ] `/price` with empty hand — prompt to hold an item
- [ ] `/bank leaderboard` — shows top 10 most depressed items with current payout
- [ ] `/bank reset` (OP) — resets all prices to base, confirmed in chat
- [ ] `/bank percentage <minutes> <pct>` (OP) — updates recovery rate, confirmed in chat
- [ ] `/bank depercentage <amount>` (OP) — updates decay multiplier, confirmed in chat
- [ ] `/bank debug` — shows loaded price count and whether IRON_INGOT/DIAMOND are listed
- [ ] Non-OP tries `/bank reset` — permission denied

## Ghost Death State

### Non-PvP deaths (purgatory applies)
- [ ] Die from fall/lava/mob — after clicking Respawn, floating player skull appears above your head
- [ ] Skull follows you as you move around the world
- [ ] Other players can see your skull floating above you
- [ ] Action bar shows purgatory reminder every second

### PvP deaths (no purgatory)
- [ ] Get killed by another player — respawn normally, no skull, no death tax
- [ ] Gold from inventory drops on floor — only you can pick it up, other players cannot
- [ ] Non-gold items from inventory drop normally — anyone can pick them up

### World restrictions while in purgatory
- [ ] Try to break a block — blocked
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

### Gold on the floor while in purgatory
- [ ] Die with 30 gold in inventory — drops on floor, leaderboard and DB show 30 (not 0)
- [ ] Items despawn after 5 minutes — leaderboard and DB drop back to 0

### `/pay death` — revival
- [ ] Run `/pay death` while NOT dead — error message, nothing happens
- [ ] Die with 0 gold across all sources — `/pay death` revives for free with "No gold to pay"
- [ ] Die with gold only in inventory (drops to floor) — 50% taken from floor items, remainder stays on floor to pick up
- [ ] Die with 30 gold on floor — pay death at 50%, 15 gold remains on floor
- [ ] Die with gold in chests/barrels and nothing on floor — 50% pulled from chests
- [ ] Die with placed gold blocks — blocks broken silently, overshoot refunded to inventory
- [ ] Die with gold spread across floor + blocks + chests — floor drained first, then blocks, then chests, then ender chest, then inventory
- [ ] Overshoot due to indivisible item (e.g. 1 ingot covers gap of 3 nuggets) — excess refunded as nuggets

## Insurance

### Subscribing
- [ ] `/insurance gold` while uninsured — shows cost breakdown with [Accept] [Decline] buttons
- [ ] Click [Accept] — charged immediately, "Gold tier activated" message, tier shows in `/insurance status`
- [ ] Click [Decline] — nothing happens, no tier set
- [ ] `/insurance gold` while already on Gold — "You are already on Gold insurance"
- [ ] `/insurance status` — shows tier, death penalty %, next charge amount, minutes until next charge
- [ ] `/insurance bronze|silver|gold` while insurance is offline — "Insurance Inc is currently offline"

### Billing cycle (20 minutes)
- [ ] Wait 20 minutes while insured — gold deducted from inventory, "charged X gold" message
- [ ] Have no gold in inventory at billing time — charged 0, tier stays active
- [ ] Disconnect and reconnect after 20 minutes — charged immediately on login (overdue catch-up)

### Tier changes
- [ ] `/insurance silver` while on Gold (downgrade) — shows "downgrade in X minutes" with [Accept]
- [ ] Accept downgrade — pending tier shown in `/insurance status`; at next billing cycle tier switches and new rate charged
- [ ] `/insurance gold` while on Bronze (upgrade) — shows "upgrade in X minutes" with [Accept]
- [ ] Accept upgrade — applies at next billing cycle
- [ ] `/insurance cancel` — "will cancel in X minutes"; tier clears at next billing cycle

### Admin commands
- [ ] `/insurance set gold 5 1.5` — updates Gold tier rates, confirmed in chat
- [ ] `/deathpenalty 40` — updates base penalty to 40%, confirmed in chat
- [ ] `/insurance off` — broadcasts "Insurance Inc is shutting down for 24 hours"
- [ ] `/insurance off` then die (non-PvP) — death tax uses base penalty regardless of active tier
- [ ] `/insurance on` — insurance resumes, OP gets confirmation
- [ ] Non-OP tries `/insurance set` — no permission

### Death penalty with insurance (non-PvP only)
- [ ] Die (non-PvP) with Gold tier active — death penalty is 8% not 50%
- [ ] Die (non-PvP) with no insurance — `/pay death` takes 50%
- [ ] Get killed by a player with Gold tier active — no purgatory, no tax charged

## Misc
- [ ] Kill a chicken — Court Officer spawns, fine note drops, fine logged to `minecraft_fines`
- [ ] Player join — profile upserted in `minecraft_players`, pending requests announced in chat
