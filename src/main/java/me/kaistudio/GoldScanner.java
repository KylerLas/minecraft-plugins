package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

            // Update sidebar scoreboard (rounded to whole gold units)
            obj.getScore(player.getName()).setScore((int) Math.round(nuggets / 9.0));

            final int total = nuggets;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().updatePlayerGold(player.getUniqueId().toString(), total)
            );
        }
    }
}
