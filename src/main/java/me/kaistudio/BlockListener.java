package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.Optional;
import java.util.UUID;

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
        Player player = event.getPlayer();

        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            Optional<UUID> owner = plugin.getChestTracker().getChestOwner(block.getLocation());
            if (owner.isPresent() && !owner.get().equals(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("That chest belongs to someone else.", NamedTextColor.RED));
                return;
            }
            plugin.getChestTracker().untrackChest(block.getLocation());
        } else if (block.getType() == Material.GOLD_BLOCK) {
            Optional<UUID> owner = plugin.getChestTracker().getGoldBlockOwner(block.getLocation());
            if (owner.isPresent() && !owner.get().equals(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("That gold block belongs to someone else.", NamedTextColor.RED));
                return;
            }
            plugin.getChestTracker().untrackGoldBlock(block.getLocation());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;

        Player player = event.getPlayer();
        Optional<UUID> owner = plugin.getChestTracker().getChestOwner(block.getLocation());
        if (owner.isPresent() && !owner.get().equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("That chest belongs to someone else.", NamedTextColor.RED));
        }
    }
}
