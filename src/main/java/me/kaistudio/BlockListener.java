package me.kaistudio;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListener implements Listener {

    private final AnnouncePlugin plugin;

    public BlockListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            plugin.getChestTracker().trackChest(block.getLocation(), player.getUniqueId());
        } else if (block.getType() == Material.GOLD_BLOCK) {
            plugin.getChestTracker().trackGoldBlock(block.getLocation(), player.getUniqueId());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            plugin.getChestTracker().untrackChest(block.getLocation());
        } else if (block.getType() == Material.GOLD_BLOCK) {
            plugin.getChestTracker().untrackGoldBlock(block.getLocation());
        }
    }
}
