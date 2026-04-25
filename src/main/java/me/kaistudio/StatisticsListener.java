package me.kaistudio;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class StatisticsListener implements Listener {

    private final AnnouncePlugin plugin;

    public StatisticsListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        // PvP kill — only counts during the purge event
        if (entity instanceof Player) {
            if (plugin.getPurgeManager().isPurgeActive()) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getDatabaseManager().incrementPvpKillsDuringPurge(killer.getUniqueId().toString()));
            }
            return;
        }

        // Boss kills — tracked separately, do not also count as mob kills
        EntityType type = entity.getType();
        if (type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN
                || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().incrementBossesKilled(killer.getUniqueId().toString()));
            return;
        }

        // Regular mob kill
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().incrementMobKills(killer.getUniqueId().toString()));
    }
}
