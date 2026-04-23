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
    │   ├── AnnouncePlugin.java        ← main plugin class, registers commands + listeners
    │   ├── ChickenDeathListener.java  ← event listener for chicken kills
    │   └── DatabaseManager.java      ← handles Cosmos DB connection + writes
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
| `/announce` | `kai.announce` (OP only) | Broadcasts a random message from the MESSAGES list as a screen title + chat message |

## Features

### /announce
- Shows the sender's name as the title in gold
- Picks a random message from the `MESSAGES` list in `AnnouncePlugin.java`
- Displays as a full-screen title + subtitle for all online players
- Also broadcasts to chat as `[PlayerName] message`
- Edit the `MESSAGES` list in `AnnouncePlugin.java` to change the messages

### Chicken Death Fines
- Listens for any chicken killed by a player
- 5 seconds after the kill:
  - Spawns a Villager named "Court Officer" at the killer's location and targets them
  - Drops a paper note (Official Fine Notice) at the killer's feet
  - Logs the fine to Cosmos DB
- Fine amount: 5 schmeckles
- Fine reason: "Killing of King Cock the 3rd"

## Database (Azure DocumentDB - MongoDB API)
Connection config lives in:
`/docker-compose/minecraft/data/plugins/AnnouncePlugin/config.yml`

Fine documents are written to the configured collection with these fields:
```json
{
  "playerName": "DeviousAF",
  "playerUuid": "...",
  "reason": "Killing of King Cock the 3rd",
  "amount": 5,
  "paid": false,
  "collected": false,
  "timestamp": "2026-04-21T..."
}
```

## Permissions
| Permission | Default | Description |
|-----------|---------|-------------|
| `kai.announce` | OP only | Allows use of `/announce` |

## Key Concepts Used
- **Commands**: Registered via Paper's Brigadier lifecycle API (`LifecycleEvents.COMMANDS`)
- **Events**: `ChickenDeathListener` implements `Listener` with `@EventHandler`
- **Scheduler**: `Bukkit.getScheduler().runTaskLater()` — 20 ticks = 1 second
- **Entity spawning**: `world.spawnEntity()` + `((Mob) entity).setTarget(player)`
- **Custom items**: `ItemStack` + `ItemMeta` with Adventure `Component` text
- **Database**: MongoDB Java driver (`mongodb-driver-sync`) connecting to Azure DocumentDB
