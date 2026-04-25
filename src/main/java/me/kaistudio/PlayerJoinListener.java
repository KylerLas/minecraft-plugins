package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class PlayerJoinListener implements Listener {

    private final AnnouncePlugin plugin;

    public PlayerJoinListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDatabaseManager().upsertPlayer(
            player.getName(),
            player.getUniqueId().toString()
        );

        if (plugin.isLeaderboardHidden(player.getUniqueId())) {
            player.setScoreboard(plugin.getBlankScoreboard());
        }

        List<Request> pending = plugin.getRequestManager().getReceived(player.getUniqueId());
        if (!pending.isEmpty()) {
            player.sendMessage(Component.text(
                "You have " + pending.size() + " pending gold request(s). Use /requests received to view them.",
                NamedTextColor.YELLOW));
        }
    }
}
