package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

public class PurgeManager {

    private final AnnouncePlugin plugin;

    private long intervalMs = 60 * 60 * 1000L;
    private long durationMs = 10 * 60 * 1000L;
    private boolean notificationsEnabled = true;
    private boolean forceNotificationsThisCycle = false;

    private boolean purgeActive = false;
    private long nextPurgeStartMs;
    private long purgeEndMs;

    private boolean notified10 = false;
    private boolean notified5  = false;
    private boolean notified1  = false;

    private BukkitTask tickTask = null;

    public PurgeManager(AnnouncePlugin plugin) {
        this.plugin = plugin;
        scheduleNext();
        startTickTask();
    }

    private void startTickTask() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // Called whenever a countdown resets. Seeds the notification flags so short intervals
    // don't fire warnings that no longer make sense (e.g. a 5-min interval won't fire a 10-min warning).
    private void scheduleNext() {
        nextPurgeStartMs = System.currentTimeMillis() + intervalMs;
        forceNotificationsThisCycle = false;
        notified10 = intervalMs <= 10 * 60 * 1000L;
        notified5  = intervalMs <= 5 * 60 * 1000L;
        notified1  = intervalMs <= 60 * 1000L;
    }

    private void tick() {
        if (purgeActive) {
            if (System.currentTimeMillis() >= purgeEndMs) endPurge();
            return;
        }

        long msLeft = nextPurgeStartMs - System.currentTimeMillis();
        if (msLeft <= 0) {
            beginPurge();
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

    // ── Public API ────────────────────────────────────────────────────────────

    public void beginPurge() {
        if (purgeActive) return;
        purgeActive = true;
        purgeEndMs = System.currentTimeMillis() + durationMs;
        plugin.getMarketManager().enterPurge();
        broadcastPurgeStart();
    }

    private void endPurge() {
        purgeActive = false;
        plugin.getMarketManager().exitPurge();
        broadcastPurgeEnd();
        scheduleNext();
    }

    // Used by /purge end — works whether a purge is active or just counting down
    public void forceEnd() {
        if (purgeActive) {
            endPurge();
        } else {
            scheduleNext();
        }
    }

    // Used by /purge begin delay — starts a fixed 10-min countdown with forced notifications
    public void beginDelay() {
        nextPurgeStartMs = System.currentTimeMillis() + 10 * 60 * 1000L;
        forceNotificationsThisCycle = true;
        notified10 = false;
        notified5  = false;
        notified1  = false;
    }

    public void addDelay(int minutes) {
        nextPurgeStartMs += (long) minutes * 60 * 1000;
    }

    public void setIntervalAndDuration(int intervalMin, int durationMin) {
        intervalMs = (long) intervalMin * 60 * 1000;
        durationMs = (long) durationMin * 60 * 1000;
        if (!purgeActive) scheduleNext();
    }

    public boolean isPurgeActive()          { return purgeActive; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean v) { notificationsEnabled = v; }

    public int getSecondsToNextPurge() {
        if (purgeActive) return 0;
        return (int) Math.max(0, (nextPurgeStartMs - System.currentTimeMillis()) / 1000);
    }

    public int getRemainingPurgeSeconds() {
        if (!purgeActive) return 0;
        return (int) Math.max(0, (purgeEndMs - System.currentTimeMillis()) / 1000);
    }

    // ── Broadcasts ────────────────────────────────────────────────────────────

    private void broadcastWarning(int minutes) {
        Bukkit.broadcast(Component.text(
            "[!] The Purge begins in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".",
            NamedTextColor.RED));
    }

    private void broadcastPurgeStart() {
        Title title = Title.title(
            Component.text("THE PURGE", NamedTextColor.RED),
            Component.text("PvP deaths count  •  2x market", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500)));
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));
        Bukkit.broadcast(Component.text(
            "☠ THE PURGE HAS BEGUN — PvP deaths send you to Purgatory! Bank sells at 2x!",
            NamedTextColor.RED));
    }

    private void broadcastPurgeEnd() {
        Bukkit.broadcast(Component.text("The Purge is over. Normal rules restored.", NamedTextColor.GRAY));
    }
}
