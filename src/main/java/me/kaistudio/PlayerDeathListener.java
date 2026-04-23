package me.kaistudio;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final AnnouncePlugin plugin;

    public PlayerDeathListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.getDatabaseManager().incrementPlayerDeaths(
            player.getName(),
            player.getUniqueId().toString()
        );
    }
}
