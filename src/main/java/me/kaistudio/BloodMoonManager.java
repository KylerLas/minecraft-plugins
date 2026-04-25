package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public class BloodMoonManager {

    private final AnnouncePlugin plugin;

    private long intervalMs = 75 * 60 * 1000L;
    private long durationMs = 15 * 60 * 1000L;
    private boolean notificationsEnabled = true;
    private boolean forceNotificationsThisCycle = false;

    private boolean bloodMoonActive = false;
    private long nextStartMs;
    private long endMs;

    private boolean notified10 = false;
    private boolean notified5  = false;
    private boolean notified1  = false;

    private BukkitTask tickTask = null;

    private final Set<UUID> bloodMoonMobs = new HashSet<>();
    private final Set<UUID> bossMobs      = new HashSet<>();

    private final Map<UUID, Integer> killCounts = new HashMap<>();
    private Scoreboard killBoard = null;
    private Objective  killObjective = null;

    private final Map<World, Integer> originalMonsterLimit = new HashMap<>();
    private final Map<World, Integer> originalMonsterTicks = new HashMap<>();

    public BloodMoonManager(AnnouncePlugin plugin) {
        this.plugin = plugin;
        scheduleNext();
        startTickTask();
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    private void startTickTask() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void scheduleNext() {
        nextStartMs = System.currentTimeMillis() + intervalMs;
        forceNotificationsThisCycle = false;
        notified10 = intervalMs <= 10 * 60 * 1000L;
        notified5  = intervalMs <= 5 * 60 * 1000L;
        notified1  = intervalMs <= 60 * 1000L;
    }

    private void tick() {
        if (bloodMoonActive) {
            if (System.currentTimeMillis() >= endMs) endBloodMoon();
            return;
        }

        long msLeft = nextStartMs - System.currentTimeMillis();
        if (msLeft <= 0) {
            beginBloodMoon();
            return;
        }

        if (!notificationsEnabled && !forceNotificationsThisCycle) return;

        long secondsLeft = msLeft / 1000;
        if (!notified10 && secondsLeft <= 600) {
            notified10 = true;
            broadcastWarning(10);
        } else if (!notified5 && secondsLeft <= 300) {
            notified5 = true;
            broadcastWarning(5);
        } else if (!notified1 && secondsLeft <= 60) {
            notified1 = true;
            broadcastWarning(1);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void beginBloodMoon() {
        if (bloodMoonActive) return;
        bloodMoonActive = true;
        endMs = System.currentTimeMillis() + durationMs;
        doubleSpawnRates();
        startKillBoard();
        broadcastStart();
    }

    private void endBloodMoon() {
        bloodMoonActive = false;
        cleanupBloodMoonMobs();
        restoreSpawnRates();
        stopKillBoard();
        scheduleNext();
    }

    public void forceEnd() {
        if (bloodMoonActive) {
            endBloodMoon();
        } else {
            scheduleNext();
        }
    }

    // Used by /bloodmoon reset — silently ends any active blood moon and resets to full interval
    public void reset() {
        if (bloodMoonActive) {
            bloodMoonActive = false;
            cleanupBloodMoonMobs();
            restoreSpawnRates();
            stopKillBoard();
        }
        scheduleNext();
    }

    public void beginDelay() {
        nextStartMs = System.currentTimeMillis() + 10 * 60 * 1000L;
        forceNotificationsThisCycle = true;
        notified10 = false;
        notified5  = false;
        notified1  = false;
    }

    // ── Spawn rate ────────────────────────────────────────────────────────────

    private void doubleSpawnRates() {
        originalMonsterLimit.clear();
        originalMonsterTicks.clear();
        for (World world : Bukkit.getWorlds()) {
            int limit = world.getMonsterSpawnLimit();
            int ticks = (int) world.getTicksPerMonsterSpawns();
            originalMonsterLimit.put(world, limit);
            originalMonsterTicks.put(world, ticks);
            world.setMonsterSpawnLimit(limit * 2);
            world.setTicksPerMonsterSpawns(Math.max(1, ticks / 2));
        }
    }

    private void restoreSpawnRates() {
        for (Map.Entry<World, Integer> e : originalMonsterLimit.entrySet())
            e.getKey().setMonsterSpawnLimit(e.getValue());
        for (Map.Entry<World, Integer> e : originalMonsterTicks.entrySet())
            e.getKey().setTicksPerMonsterSpawns(e.getValue());
        originalMonsterLimit.clear();
        originalMonsterTicks.clear();
    }

    // ── Mob tracking ──────────────────────────────────────────────────────────

    public void trackMob(UUID uuid, boolean isBoss) {
        bloodMoonMobs.add(uuid);
        if (isBoss) bossMobs.add(uuid);
    }

    public void untrackMob(UUID uuid) {
        bloodMoonMobs.remove(uuid);
        bossMobs.remove(uuid);
    }

    public boolean isBloodMoonMob(UUID uuid) { return bloodMoonMobs.contains(uuid); }
    public boolean isBoss(UUID uuid)          { return bossMobs.contains(uuid); }

    private void cleanupBloodMoonMobs() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (bloodMoonMobs.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
        bloodMoonMobs.clear();
        bossMobs.clear();
    }

    // ── Kill board ────────────────────────────────────────────────────────────

    private void startKillBoard() {
        killCounts.clear();
        killBoard = Bukkit.getScoreboardManager().getNewScoreboard();
        killObjective = killBoard.registerNewObjective(
            "bmkills", "dummy",
            Component.text("☽ Blood Moon Kills", NamedTextColor.RED));
        killObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyBoardToPlayer(p);
        }
    }

    private void stopKillBoard() {
        // Reward top killer if 3+ players were online
        if (Bukkit.getOnlinePlayers().size() >= 3 && !killCounts.isEmpty()) {
            UUID topUuid = Collections.max(killCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
            Player topPlayer = Bukkit.getPlayer(topUuid);
            if (topPlayer != null) {
                int kills = killCounts.get(topUuid);
                GoldUtil.addGold(topPlayer, 180); // 20 gold ingots = 180 nuggets
                Bukkit.broadcast(Component.text(
                    "☽ Blood Moon over! " + topPlayer.getName() + " led with "
                    + kills + " kill" + (kills == 1 ? "" : "s") + " and earned 20 gold!",
                    NamedTextColor.GOLD));
            } else {
                broadcastEnd();
            }
        } else {
            broadcastEnd();
        }

        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(main);
        }
        killBoard = null;
        killObjective = null;
        killCounts.clear();
    }

    public void applyBoardToPlayer(Player player) {
        if (killBoard == null || killObjective == null) return;
        player.setScoreboard(killBoard);
        killObjective.getScore(player.getName()).setScore(
            killCounts.getOrDefault(player.getUniqueId(), 0));
    }

    public void recordKill(Player player, boolean isBoss) {
        if (!bloodMoonActive) return;
        int points = isBoss ? 3 : 1;
        int newCount = killCounts.merge(player.getUniqueId(), points, Integer::sum);
        if (killObjective != null) {
            killObjective.getScore(player.getName()).setScore(newCount);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isBloodMoonActive()          { return bloodMoonActive; }
    public boolean isNotificationsEnabled()     { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean v) { notificationsEnabled = v; }

    public void addDelay(int minutes) {
        nextStartMs += (long) minutes * 60 * 1000;
    }

    public void setIntervalAndDuration(int intervalMin, int durationMin) {
        intervalMs = (long) intervalMin * 60 * 1000;
        durationMs = (long) durationMin * 60 * 1000;
        if (!bloodMoonActive) scheduleNext();
    }

    public int getSecondsToNext() {
        if (bloodMoonActive) return 0;
        return (int) Math.max(0, (nextStartMs - System.currentTimeMillis()) / 1000);
    }

    public int getRemainingSeconds() {
        if (!bloodMoonActive) return 0;
        return (int) Math.max(0, (endMs - System.currentTimeMillis()) / 1000);
    }

    // ── Broadcasts ────────────────────────────────────────────────────────────

    private void broadcastWarning(int minutes) {
        Bukkit.broadcast(Component.text(
            "[!] A Blood Moon rises in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".",
            NamedTextColor.DARK_RED));
    }

    private void broadcastStart() {
        Title title = Title.title(
            Component.text("BLOOD MOON", NamedTextColor.DARK_RED),
            Component.text("Stronger mobs  •  Top killer wins 20 gold", NamedTextColor.RED),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500)));
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));
        Bukkit.broadcast(Component.text(
            "☽ A BLOOD MOON RISES — Mobs are stronger and more numerous. Top killer wins 20 gold!",
            NamedTextColor.DARK_RED));
    }

    private void broadcastEnd() {
        Bukkit.broadcast(Component.text("The Blood Moon has passed.", NamedTextColor.GRAY));
    }
}
