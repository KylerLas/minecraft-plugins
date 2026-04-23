package me.kaistudio;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GoldScanner implements Runnable {

    private final AnnouncePlugin plugin;

    public GoldScanner(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            int nuggets = GoldUtil.countNuggets(player.getInventory());

            // Chest gold — deduplicate double chests by inventory reference
            Set<Inventory> counted = new HashSet<>();
            List<Location> chestLocs = plugin.getChestTracker().getChestLocations(player.getUniqueId());
            for (Location loc : chestLocs) {
                Block block = loc.getBlock();
                if (!(block.getState() instanceof Chest chest)) continue;
                Inventory inv = chest.getInventory();
                if (!counted.add(inv)) continue;
                nuggets += GoldUtil.countNuggets(inv);
            }

            // Placed gold blocks (each = 81 nuggets)
            nuggets += plugin.getChestTracker().getGoldBlockCount(player.getUniqueId()) * 81;

            final int total = nuggets;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().updatePlayerGold(player.getUniqueId().toString(), total)
            );
        }
    }
}
