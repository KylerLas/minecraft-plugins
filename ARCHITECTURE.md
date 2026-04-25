# Architecture

Quick navigation guide for Claude. Jump here first when adding a feature or fixing a bug.

---

## File Map

| File | Owns |
|------|------|
| `AnnouncePlugin.java` | Plugin entry point — registers all listeners and commands in `onEnable()` |
| `DatabaseManager.java` | All Cosmos DB reads/writes — one method per operation |
| `ChestTracker.java` | YAML-backed in-memory map of chest and gold block locations → owner UUID |
| `GoldUtil.java` | Gold counting, formatting, add/remove from inventory; `isTrackedGold(Material)` shared helper |
| `GoldScanner.java` | Repeating task (every 10s) — tallies gold, updates DB + sidebar scoreboard |
| `GoldScoreCommand.java` | `/goldscore` — reads scoreboard and prints leaderboard to chat |
| `GoldDropListener.java` | Tracks dropped gold item entities (nugget/ingot/block/ore) — only the dropper can pick them up |
| `GoldRestrictionListener.java` | Blocks gold items from being placed into shulker boxes, hoppers, droppers, dispensers |
| `BlockListener.java` | Block place/break tracking; chest/barrel/ender chest ownership protection; view-only enforcement |
| `PayCommand.java` | `/pay <player> <amount>` — physical gold transfer via inventory |
| `RequestCommand.java` | `/request` and `/requests` — gold request flow with clickable chat buttons |
| `RequestManager.java` | In-memory store of pending `Request` objects |
| `Request.java` | Data class: requester, target, amount, status |
| `PlayerJoinListener.java` | Upserts player profile on join; notifies of pending requests |
| `PlayerDeathListener.java` | Increments death counter on death |
| `ChickenDeathListener.java` | Chicken kill → Court Officer spawns, fine note drops, fine logged to DB |
| `DeathStateManager.java` | YAML-backed ghost state — dead player set, skull ArmorStand tracking, gold collection logic |
| `DeathStateListener.java` | All ghost state events — death, respawn, join, quit, move, block, interact, damage, drop |
| `InsuranceManager.java` | Config cache, 20-min billing task, tier subscriptions, pending tier changes, tax rate lookup |
| `InsuranceCommand.java` | `/insurance` and `/deathpenalty` commands |
| `InsuranceListener.java` | Player join/quit — loads and unloads per-player insurance state |
| `DeathPenaltyCommand.java` | `/deathpenalty <pct>` — OP-only base penalty override |

---

## Where to Find Specific Functionality

| I want to… | Look in |
|------------|---------|
| Add a new command | Create a new `Command` class, register in `AnnouncePlugin.onEnable()` |
| Add a new DB operation | Add a method to `DatabaseManager` |
| Change gold counting logic | `GoldUtil.countNuggets()` |
| Change gold display format | `GoldUtil.format()` |
| Change how gold is added/removed from inventory | `GoldUtil.addGold()` / `GoldUtil.removeGold()` |
| Change how often gold is scanned | `GoldScanner` task interval in `AnnouncePlugin.onEnable()` |
| Change what gold counts toward a player's total | `GoldScanner.run()` |
| Change scoreboard display | `GoldScanner.run()` (sidebar) or `GoldScoreCommand` (chat) |
| Add a new tracked block type | `BlockListener.onBlockPlace()` + `BlockListener.onBlockBreak()` |
| Change chest/barrel ownership rules | `BlockListener` + `ChestTracker` |
| Change what counts toward a player's gold total | `GoldScanner.run()` — inventory, tracked chests/barrels, ender chest, placed gold blocks |
| Change who can pick up dropped items | `GoldDropListener` |
| Change request lifecycle | `RequestCommand` + `RequestManager` |
| Add a player profile field | `DatabaseManager.upsertPlayer()` + DB schema in `CLAUDE.md` |
| Change fine behaviour | `ChickenDeathListener` + `DatabaseManager.logFine()` |
| Change death penalty rate | `InsuranceManager.getDeathTaxRate()` — reads player tier from cache, falls back to `baseDeathPenalty` |
| Adjust tier rates in-game | `/insurance set <tier> <death%> <daily%>` (OP) — persists to `minecraft_config` |
| Adjust base death penalty | `/deathpenalty <pct>` (OP) — persists to `minecraft_config` |
| Change billing interval | `InsuranceManager.BILLING_INTERVAL_MS` constant |
| Change ghost state restrictions | `DeathStateListener` event handlers |
| Change skull position | `DeathStateManager.skullLocation()` — currently +2.1 Y above player feet |
| Change gold removal order on death | `DeathStateManager.collectGold()` |

---

## Key Patterns

**Commands** — registered via Paper's Brigadier lifecycle API:
```java
getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
    event.registrar().register(new MyCommand(this).build(), "description");
});
```

**Events** — listeners implement `Listener` with `@EventHandler`, registered in `AnnouncePlugin.onEnable()`:
```java
Bukkit.getPluginManager().registerEvents(new MyListener(this), this);
```

**Async DB writes** — all DB calls go through `runTaskAsynchronously` to avoid blocking the main thread:
```java
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getDatabaseManager().someMethod(...));
```

**Upsert pattern** — used for player profiles so it's safe to call on every join:
```java
collection.updateOne(filter, updates, new UpdateOptions().upsert(true));
```

**Storage ownership lookup** (chests, barrels, ender chests all use the same tracker):
```java
plugin.getChestTracker().getChestOwner(block.getLocation()) // → Optional<UUID>
// Double chests: check both halves via DoubleChest.getLeftSide() / getRightSide()
// Ender chests: tracked for break protection only — inventory always belongs to the opening player
```

**Clickable chat buttons** — used in `/requests`:
```java
Component.text("[Accept]").clickEvent(ClickEvent.runCommand("/request accept " + id))
```

---

## Build Notes
- Maven and Java are NOT installed in code-server — the deploy script spins up a temporary `maven:3.9-eclipse-temurin-21` Docker container to compile
- The MongoDB driver is bundled into the JAR via the Maven Shade plugin
- Docker binary lives at `~/.local/bin/docker` — always prefix deploy with `export PATH="$HOME/.local/bin:$PATH"`
- The `target/` folder is git-ignored — build artifacts are never committed
