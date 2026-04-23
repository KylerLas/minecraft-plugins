# my-first-plugin

A Paper Minecraft plugin for Kai's server running on TrueNAS with Docker.

## Server Details
- **Server**: Paper 1.21.11, Java 25
- **Container**: `minecraft` (Docker)
- **Data path (host)**: `/mnt/storage/docker-compose/minecraft/data`
- **Data path (code-server)**: `/docker-compose/minecraft/data`

## Project Structure
```
my-first-plugin/
├── deploy.sh                          ← build + deploy script
├── pom.xml                            ← Maven config + dependencies
├── CLAUDE.md                          ← this file
└── src/main/
    ├── java/me/kaistudio/
    │   ├── AnnouncePlugin.java        ← main plugin class, registers all commands + listeners
    │   ├── DatabaseManager.java       ← Cosmos DB connection + all write operations
    │   ├── ChickenDeathListener.java  ← chicken kill → issues fine + logs to DB
    │   ├── PlayerJoinListener.java    ← upserts player profile; notifies of pending requests
    │   ├── PlayerDeathListener.java   ← increments death counter on player death
    │   ├── BlockListener.java         ← tracks chest and gold block place/break events; enforces ownership protection
    │   ├── GoldScanner.java           ← repeating task every 10s: tallies gold, updates DB + scoreboard
    │   ├── GoldUtil.java              ← gold counting, rounding, add/remove from inventory
    │   ├── GoldScoreCommand.java      ← /goldscore — shows sorted leaderboard in chat
    │   ├── PayCommand.java            ← /pay command logic
    │   ├── RequestCommand.java        ← /request and /requests command logic
    │   ├── RequestManager.java        ← in-memory store of pending gold requests
    │   ├── Request.java               ← request data class
    │   ├── GoldDropListener.java      ← tracks dropped gold items; only the dropper can pick them up
    │   └── ChestTracker.java          ← YAML-backed tracker for player chests + placed gold blocks
    └── resources/
        ├── plugin.yml                 ← plugin metadata + permission declarations
        └── config.yml                 ← default config template (connection string goes here)
```

## How to Deploy
Run from the code-server terminal:
```bash
export PATH="$HOME/.local/bin:$PATH" && bash /docker-compose/minecraft/plugin-dev/my-first-plugin/deploy.sh
```

The deploy script:
1. Builds the JAR using a Maven Docker container (`maven:3.9-eclipse-temurin-21`)
2. Copies the JAR into the minecraft container via `docker cp`
3. Clears the `.paper-remapped` cache so Paper picks up the new JAR
4. Fixes plugin folder permissions (chmod 777) so code-server can write configs
5. Restarts the minecraft container

## Build Notes
- Maven and Java are not installed in code-server — the deploy script uses a temporary Maven Docker container to compile
- The MongoDB driver is bundled into the JAR via the Maven Shade plugin (it's not provided by the server)
- Docker binary lives at `~/.local/bin/docker` — always needs `export PATH="$HOME/.local/bin:$PATH"` before running docker commands
- Docker socket at `/var/run/docker.sock` needs `chmod 666` after a TrueNAS reboot (run `sudo chmod 666 /var/run/docker.sock` from TrueNAS shell)

## Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/announce` | `kai.announce` (OP only) | Broadcasts a random message as a screen title + chat message |
| `/pay <player> <amount>` | anyone | Pay another player gold from your inventory only |
| `/request <player> <amount>` | anyone | Request gold from another player |
| `/requests sent` | anyone | View outgoing requests with [Cancel] buttons |
| `/requests received` | anyone | View incoming requests with [Accept] / [Decline] buttons |
| `/goldscore` | anyone | Show full sorted gold leaderboard in chat |

## Integration Progress

- [x] Fine logging (`minecraft_fines` collection)
- [x] Player profile creation on join (`minecraft_players`)
- [x] Player death tracking (`minecraft_players`)
- [x] Gold balance — live scan every 10s, inventory + chests + placed gold blocks (`minecraft_players`)
- [x] Transaction tracking — sent/received via `/pay` and `/request` (`minecraft_players`)
- [x] Gold leaderboard — sidebar scoreboard (always visible, right side of screen) + `/goldscore`
- [x] Block/chest ownership protection — players cannot break others' blocks; chests are view-only for non-owners
- [x] Gold drop ownership — only the dropper can pick up gold they dropped; enforces /pay and /request usage
- [ ] Insurance tier — `/insurance` command (`minecraft_players`)

---

## Features

### /announce
- Shows the sender's name as the title in gold
- Picks a random message from the `MESSAGES` list in `AnnouncePlugin.java`
- Displays as a full-screen title + subtitle for all online players
- Also broadcasts to chat as `[PlayerName] message`

### Chicken Death Fines
- Listens for any chicken killed by a player
- 5 seconds after the kill:
  - Spawns a Villager named "Court Officer" at the killer's location and targets them
  - Drops a paper note (Official Fine Notice) at the killer's feet
  - Logs the fine to `minecraft_fines` in Cosmos DB
- Fine amount: 5 schmeckles
- Fine reason: "Killing of King Cock the 3rd"

### Player Profiles
- **On join**: `PlayerJoinListener` upserts the player document in `minecraft_players`
  - Creates document with all fields defaulted to zero/null if the player is new
  - Updates `playerName` and `lastSeen` on every join for existing players
  - Notifies the player of any pending incoming gold requests
- **On death**: `PlayerDeathListener` increments `deaths` by 1 and updates `lastSeen`

### Gold Economy
- **Values**: nugget = 1/9 gold, ingot = 1 gold, block = 9 gold (stored internally as nugget units)
- **Display**: all gold amounts rounded to nearest whole number (`Math.round`)
- **`/pay <player> <amount>`**: checks sender's inventory only — inventory is the wallet
  - Removes gold physically (handles change: removes all gold, gives back the difference)
  - Gold drops at recipient's feet if their inventory is full
  - Updates `transactionsSent` / `transactionsReceived` in DB
- **`/request <player> <amount>`**: sends a gold request to another player
  - Target is notified immediately if online; notified on next login if offline
  - If target accepts but lacks sufficient inventory gold → request stays pending
  - If requester goes offline when target tries to accept → request is cancelled
  - `/requests sent` and `/requests received` with clickable [Accept] / [Decline] / [Cancel] buttons
- **Gold Scanner** (`GoldScanner.java`): runs every 10 seconds on the main thread
  - Tallies inventory gold + chest gold (deduplicates double chests) + placed gold blocks
  - Updates `gold` field in DB (async)
  - Updates sidebar scoreboard (auto-sorted descending by Minecraft)
- **`/goldscore`**: shows full leaderboard in chat — gold/silver/bronze colours for top 3

### Chest & Gold Block Tracking
- **Chest placed** → recorded in `chest_tracker.yml` under the placing player's UUID
- **Chest broken** → removed from `chest_tracker.yml`
- **Gold block placed** → recorded as 81 nuggets (9 gold) owned by that player
- **Gold block broken** → removed from tracker; item drops naturally and is picked up by next scan

### Block & Chest Ownership Protection
- **Break protection**: if a player tries to break a tracked chest or gold block they don't own, the event is cancelled and they receive a red error message
- **Chest view-only**: other players can open and view a chest's contents but cannot move, take, place, or shift-click items — `InventoryClickEvent` and `InventoryDragEvent` are cancelled for non-owners; double chests check both halves
- Untracked chests and gold blocks (placed before the plugin was installed) are unprotected

### Gold Drop Ownership
- When a player drops a gold nugget, ingot, or block, the item entity UUID is recorded in `GoldDropListener`
- Other players' `EntityPickupItemEvent` is cancelled for that item — only the dropper can pick it back up
- Entries are cleaned up when the item is picked up by the owner or despawns
- Enforces use of `/pay` and `/request` for gold transfers

---

## Database (Azure Cosmos DB — MongoDB API)

Connection config lives in:
`/docker-compose/minecraft/data/plugins/AnnouncePlugin/config.yml`

### Collection: `minecraft_fines`
Immutable event log — insert only, never updated.

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
Mutable player profile — one document per UUID, upserted by the plugin.

```json
{
  "playerName": "DeviousAF",
  "playerUuid": "...",
  "deaths": 3,
  "gold": 45,
  "transactionsSent": 2,
  "transactionsReceived": 1,
  "insuranceTier": null,
  "lastSeen": "2026-04-23T...",
  "joinDate": "2026-04-21T..."
}
```

`gold` is stored as a decimal gold value (e.g. `11.89`), rounded to 2 decimal places. 1 ingot = 1, 1 block = 9, 1 nugget = 1/9. Round to nearest whole number for display.

### DatabaseManager methods
| Method | Collection | Operation |
|--------|-----------|-----------|
| `logFine(...)` | `minecraft_fines` | Insert new fine document |
| `upsertPlayer(...)` | `minecraft_players` | Create profile if new; update name + lastSeen if existing |
| `incrementPlayerDeaths(...)` | `minecraft_players` | Increment `deaths` by 1, update `lastSeen` |
| `updatePlayerGold(...)` | `minecraft_players` | Set `gold` to current nugget total |
| `incrementTransactionsSent(...)` | `minecraft_players` | Increment `transactionsSent` by 1 |
| `incrementTransactionsReceived(...)` | `minecraft_players` | Increment `transactionsReceived` by 1 |

---

## Permissions
| Permission | Default | Description |
|-----------|---------|-------------|
| `kai.announce` | OP only | Allows use of `/announce` |

## Key Concepts Used
- **Commands**: Registered via Paper's Brigadier lifecycle API (`LifecycleEvents.COMMANDS`)
- **Events**: Listeners implement `Listener` with `@EventHandler` — registered in `AnnouncePlugin.onEnable()`
- **Scheduler**: `runTaskTimer` for repeating tasks (GoldScanner), `runTaskLater` for delayed actions
- **Scoreboard**: Main scoreboard sidebar objective updated every 10s — Minecraft auto-sorts descending
- **Clickable chat**: Adventure `ClickEvent.runCommand()` for accept/decline/cancel buttons
- **Entity spawning**: `world.spawnEntity()` + `((Mob) entity).setTarget(player)`
- **Custom items**: `ItemStack` + `ItemMeta` with Adventure `Component` text
- **Database**: MongoDB Java driver (`mongodb-driver-sync`) connecting to Azure Cosmos DB (MongoDB API)
- **Upsert pattern**: `updateOne` with `UpdateOptions().upsert(true)` — safe to call repeatedly
- **Async DB writes**: `runTaskAsynchronously` for all DB operations to avoid blocking the main thread
