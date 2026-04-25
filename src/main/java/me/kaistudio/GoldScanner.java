package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GoldScanner implements Runnable {

    private static final String OBJECTIVE_NAME = "gold_lb";

    private final AnnouncePlugin plugin;

    public GoldScanner(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                Component.text("✦ Gold Leaderboard", NamedTextColor.GOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            int nuggets = GoldUtil.countNuggets(player.getInventory());

            // Chests and barrels — deduplicate double chests by inventory reference
            // Ender chest locations are tracked for break protection but skipped here
            Set<Inventory> counted = new HashSet<>();
            List<Location> trackedLocs = plugin.getChestTracker().getChestLocations(player.getUniqueId());
            for (Location loc : trackedLocs) {
                Block block = loc.getBlock();
                Inventory inv = null;
                if (block.getState() instanceof Chest chest) {
                    inv = chest.getInventory();
                } else if (block.getState() instanceof Barrel barrel) {
                    inv = barrel.getInventory();
                }
                // Ender chest blocks return null here and are intentionally skipped
                if (inv == null || !counted.add(inv)) continue;
                nuggets += GoldUtil.countNuggets(inv);
            }

            // Ender chest — always the player's own private inventory, scanned directly
            nuggets += GoldUtil.countNuggets(player.getEnderChest());

            // Floor items (death drops and manual drops owned by this player)
            nuggets += plugin.getGoldDropListener().countNuggets(player.getUniqueId());

            // Placed gold blocks — verify each still exists and prune stale entries
            for (Location loc : plugin.getChestTracker().getGoldBlockLocations(player.getUniqueId())) {
                if (loc.getBlock().getType() == org.bukkit.Material.GOLD_BLOCK) {
                    nuggets += 81;
                } else {
                    plugin.getChestTracker().untrackGoldBlock(loc);
                }
            }

            // Update sidebar scoreboard (rounded to whole gold units)
            obj.getScore(player.getName()).setScore((int) Math.round(nuggets / 9.0));

            final double gold = Math.round((nuggets / 9.0) * 100) / 100.0;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().updatePlayerGold(player.getUniqueId().toString(), gold)
            );
        }
    }
}
