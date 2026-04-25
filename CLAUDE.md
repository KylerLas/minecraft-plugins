# my-first-plugin

A Paper Minecraft plugin for Kai's private server running on TrueNAS with Docker. The goal is to build a lightweight gold-based player economy with leaderboards, chest/block ownership, transaction commands, and eventually an insurance system — all backed by Azure Cosmos DB.

See [ARCHITECTURE.md](ARCHITECTURE.md) for file-by-file navigation and implementation patterns.
See [TEST.md](TEST.md) for the manual QA checklist.

---

## Server Details

- **Server**: Paper 1.21.11, Java 25
- **Container**: `minecraft` (Docker)
- **Data path (host)**: `/mnt/storage/docker-compose/minecraft/data`
- **Data path (code-server)**: `/docker-compose/minecraft/data`

---

## How to Deploy

Run from the code-server terminal:

```bash
export PATH="$HOME/.local/bin:$PATH" && bash /docker-compose/minecraft/plugin-dev/my-first-plugin/deploy.sh
```

The deploy script builds the JAR via a temporary Maven Docker container, copies it into the minecraft container, clears the Paper remap cache, fixes permissions, and restarts the server.

> **After a TrueNAS reboot**: run `sudo chmod 666 /var/run/docker.sock` from the TrueNAS shell before deploying.

---

## Gold Economy Rules

- 1 gold nugget = 1/9 gold
- 1 gold ingot = 1 gold
- 1 gold block = 9 gold
- **DB stores gold as a decimal rounded to 2 places** (e.g. `11.89`)
- **Display always rounds to the nearest whole number** (e.g. `12`)
- A player's total gold = inventory + owned chests + owned barrels + ender chest + placed gold blocks
- Gold can only be transferred between players via `/pay` or `/request` — dropping gold locks it to the dropper

---

## Commands

| Command                      | Permission               | Description                                              |
| ---------------------------- | ------------------------ | -------------------------------------------------------- |
| `/announce`                  | `kai.announce` (OP only) | Broadcasts a random message as title + chat              |
| `/pay <player> <amount>`     | anyone                   | Pay another player gold from your inventory              |
| `/request <player> <amount>` | anyone                   | Request gold from another player                         |
| `/requests sent`             | anyone                   | View outgoing requests with [Cancel] buttons             |
| `/requests received`         | anyone                   | View incoming requests with [Accept] / [Decline] buttons |
| `/goldscore`                 | anyone                   | Show full sorted gold leaderboard in chat                |
| `/pay death`                 | anyone (dead only)       | Pay the death penalty to exit ghost state                |
| `/insurance bronze\|silver\|gold` | anyone              | Subscribe to or change insurance tier                    |
| `/insurance cancel`          | anyone                   | Cancel insurance at end of current cycle                 |
| `/insurance status`          | anyone                   | Show current tier, death penalty %, next charge          |
| `/insurance set <tier> <death%> <daily%>` | `kai.insurance` | Update tier rates                           |
| `/insurance on\|off`         | `kai.insurance`          | Enable/disable insurance for all players                 |
| `/deathpenalty <percentage>` | `kai.insurance`          | Adjust the base (no insurance) death penalty             |

---

## Integration Progress

- [x] Fine logging (`minecraft_fines` collection)
- [x] Player profile creation on join (`minecraft_players`)
- [x] Player death tracking (`minecraft_players`)
- [x] Gold balance — live scan every 10s, inventory + chests + placed gold blocks
- [x] Transaction tracking — sent/received via `/pay` and `/request`
- [x] Gold leaderboard — sidebar scoreboard + `/goldscore`
- [x] Block/chest/barrel/ender chest ownership — break protection + view-only for chests and barrels
- [x] Gold drop ownership — dropped gold (nugget/ingot/block/ore) can only be picked up by the dropper
- [x] Gold storage restriction — gold items cannot be placed into shulker boxes, hoppers, droppers, or dispensers
- [x] Barrel and ender chest gold tracking — barrel contents + player's ender chest scanned every 10s
- [x] Ghost death state — on death, player enters ghost mode with floating skull; `/pay death` charges death penalty % of total gold to revive
- [x] Insurance tiers — Bronze/Silver/Gold policies with recurring 20-minute billing; `/insurance`, `/deathpenalty`, `/insurance on|off`

---

## Ghost Death State

When a player dies they enter ghost mode after respawning:

- An invisible ArmorStand with their player skull floats above their head, following them via `PlayerMoveEvent`
- They can roam freely but cannot break/place blocks, interact with the world, attack, drop items, or receive damage
- Other players can see the skull but cannot attack or interact with the ghost
- Ghost state persists through disconnects — skull respawns on next login
- Orphan skull ArmorStands from previous server runs are cleaned up on plugin enable via a `PersistentDataContainer` tag (`myfirstplugin:death_skull`)

### Insurance tiers

Players subscribe via `/insurance bronze|silver|gold`. Default rates (adjustable via `/insurance set`):

| Tier | Death Penalty | Daily Cost (% of total gold) |
|---|:---:|:---:|
| Bronze | 35% | 0.5% |
| Silver | 20% | 1% |
| Gold | 8% | 2% |

Billing fires every 20 real-world minutes. Cost = % of total gold, taken from inventory. If a tier change is pending it applies at the next billing cycle. Config persists in `minecraft_config` collection (`_id = "insurance_rates"`). Dashboard reads rates from `GET /api/insurance-config`.

### Revival — `/pay death`

Charges 50% of the player's total gold (same pool as GoldScanner: inventory + chests + barrels + ender chest + placed gold blocks).

**Removal order (stop early once debt is covered):**
1. Placed gold blocks — broken silently one at a time; overage is refunded to inventory
2. Owned chests and barrels
3. Ender chest
4. Inventory

If total gold is 0, revival is free.

### Insurance hook

`DeathStateManager.getDeathTaxRate(UUID)` returns `0.50` by default. When insurance tiers land, this method will read `insuranceTier` from the player document and map it to the appropriate rate — no other structural changes needed.

---

## Database (Azure Cosmos DB — MongoDB API)

Connection string lives in:
`/docker-compose/minecraft/data/plugins/AnnouncePlugin/config.yml`

### Collection: `minecraft_fines`

Insert-only event log.

```json
{
  "playerName": "DeviousAF",
  "playerUuid": "...",
  "reason": "Killing of King Cock the 3rd",
  "amount": 5,
  "paid": false,
  "collected": false,
  "timestamp": "2026-04-23T..."
}
```

### Collection: `minecraft_players`

One document per player UUID, upserted by the plugin.

```json
{
  "playerName": "DeviousAF",
  "playerUuid": "...",
  "deaths": 3,
  "gold": 11.89,
  "transactionsSent": 2,
  "transactionsReceived": 1,
  "insuranceTier": null,
  "lastSeen": "2026-04-23T...",
  "joinDate": "2026-04-21T..."
}
```
