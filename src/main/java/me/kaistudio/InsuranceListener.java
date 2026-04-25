package me.kaistudio;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class InsuranceListener implements Listener {

    private final AnnouncePlugin plugin;

    public InsuranceListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getInsuranceManager().loadPlayerState(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getInsuranceManager().unloadPlayerState(event.getPlayer().getUniqueId());
    }
}
