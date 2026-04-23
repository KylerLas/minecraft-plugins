package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

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

    // Allow opening other players' chests to view, but block any interaction that moves items
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isOtherPlayersChest(top, player)) return;

        // Block clicks directly inside the chest
        if (top.equals(event.getClickedInventory())) {
            event.setCancelled(true);
            return;
        }

        // Block shift-clicks from player's own inventory into the chest
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isOtherPlayersChest(event.getView().getTopInventory(), player)) return;

        // Block any drag that touches slots in the chest (slot indices < top inventory size)
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isOtherPlayersChest(Inventory inv, Player player) {
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof org.bukkit.block.Chest chest) {
            return isLockedChest(chest.getLocation(), player);
        }
        if (holder instanceof DoubleChest doubleChest) {
            if (doubleChest.getLeftSide() instanceof org.bukkit.block.Chest left
                    && isLockedChest(left.getLocation(), player)) return true;
            if (doubleChest.getRightSide() instanceof org.bukkit.block.Chest right
                    && isLockedChest(right.getLocation(), player)) return true;
        }
        return false;
    }

    private boolean isLockedChest(org.bukkit.Location loc, Player player) {
        Optional<UUID> owner = plugin.getChestTracker().getChestOwner(loc);
        return owner.isPresent() && !owner.get().equals(player.getUniqueId());
    }
}
