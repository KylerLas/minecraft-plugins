package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GoldRestrictionListener implements Listener {

    private static final Component BLOCKED_MSG = Component.text(
        "Gold cannot be stored in shulker boxes or automation blocks.", NamedTextColor.RED
    );

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isRestrictedContainer(top)) return;

        InventoryAction action = event.getAction();

        // Clicking inside the restricted container with a gold item on cursor
        if (top.equals(event.getClickedInventory()) && isGold(event.getCursor())) {
            event.setCancelled(true);
            player.sendMessage(BLOCKED_MSG);
            return;
        }

        // Shift-clicking gold from player inventory into the restricted container
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && isGold(event.getCurrentItem())) {
            event.setCancelled(true);
            player.sendMessage(BLOCKED_MSG);
            return;
        }

        // Hotbar-swapping a gold item into a slot in the restricted container
        if (action == InventoryAction.HOTBAR_SWAP && top.equals(event.getClickedInventory())) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0 && isGold(player.getInventory().getItem(hotbarSlot))) {
                event.setCancelled(true);
                player.sendMessage(BLOCKED_MSG);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isRestrictedContainer(top)) return;
        if (!isGold(event.getOldCursor())) return;

        int topSize = top.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                player.sendMessage(BLOCKED_MSG);
                return;
            }
        }
    }

    private boolean isRestrictedContainer(Inventory inv) {
        InventoryType type = inv.getType();
        return type == InventoryType.SHULKER_BOX
            || type == InventoryType.HOPPER
            || type == InventoryType.DROPPER
            || type == InventoryType.DISPENSER;
    }

    private boolean isGold(ItemStack item) {
        return item != null && GoldUtil.isTrackedGold(item.getType());
    }
}
