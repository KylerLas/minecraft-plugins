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
- A player's total gold = inventory + owned chests + placed gold blocks
- Gold can only be transferred between players via `/pay` or `/request` — dropping gold locks it to the dropper

---

## Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/announce` | `kai.announce` (OP only) | Broadcasts a random message as title + chat |
| `/pay <player> <amount>` | anyone | Pay another player gold from your inventory |
| `/request <player> <amount>` | anyone | Request gold from another player |
| `/requests sent` | anyone | View outgoing requests with [Cancel] buttons |
| `/requests received` | anyone | View incoming requests with [Accept] / [Decline] buttons |
| `/goldscore` | anyone | Show full sorted gold leaderboard in chat |

---

## Integration Progress
- [x] Fine logging (`minecraft_fines` collection)
- [x] Player profile creation on join (`minecraft_players`)
- [x] Player death tracking (`minecraft_players`)
- [x] Gold balance — live scan every 10s, inventory + chests + placed gold blocks
- [x] Transaction tracking — sent/received via `/pay` and `/request`
- [x] Gold leaderboard — sidebar scoreboard + `/goldscore`
- [x] Block/chest ownership — players cannot break others' blocks; chests are view-only for non-owners
- [x] Gold drop ownership — dropped gold can only be picked up by the dropper
- [ ] Insurance tier — `/insurance` command (`minecraft_players`)

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
