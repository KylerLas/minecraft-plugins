package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class StatisticsListener implements Listener {

    private static final int GUARDIAN_GOLD   = 75;
    private static final int WITHER_GOLD     = 220;
    private static final int DRAGON_GOLD     = 350;

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

        EntityType type = entity.getType();

        // Boss kills — tracked per type, rewarded with gold purse, announced server-wide
        switch (type) {
            case ELDER_GUARDIAN -> {
                awardPurse(killer, GUARDIAN_GOLD, "Elder Guardian");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getDatabaseManager().incrementGuardianKills(killer.getUniqueId().toString()));
                return;
            }
            case WITHER -> {
                awardPurse(killer, WITHER_GOLD, "Wither");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getDatabaseManager().incrementWitherKills(killer.getUniqueId().toString()));
                return;
            }
            case ENDER_DRAGON -> {
                awardPurse(killer, DRAGON_GOLD, "Ender Dragon");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    plugin.getDatabaseManager().incrementDragonKills(killer.getUniqueId().toString()));
                return;
            }
            default -> {}
        }

        // Regular guardian is a mob kill, not a boss
        if (type == EntityType.GUARDIAN) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().incrementMobKills(killer.getUniqueId().toString()));
            return;
        }

        // All other non-player mob kills
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().incrementMobKills(killer.getUniqueId().toString()));
    }

    private void awardPurse(Player killer, int gold, String bossName) {
        GoldUtil.addGold(killer, gold * 9);
        killer.sendMessage(Component.text(
            "Boss Purse: You slew the " + bossName + " and received " + gold + " gold!",
            NamedTextColor.GOLD));
        Bukkit.broadcast(
            Component.text(killer.getName() + " slew the " + bossName + " and claimed a " + gold + " gold purse!",
                NamedTextColor.GOLD));
    }
}
