package me.kaistudio;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GoldDropListener implements Listener {

    private final Map<UUID, UUID> droppedGold = new HashMap<>(); // item entity UUID → owner UUID

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        Material type = item.getItemStack().getType();
        if (GoldUtil.isTrackedGold(type)) {
            droppedGold.put(item.getUniqueId(), event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID itemId = event.getItem().getUniqueId();
        UUID owner = droppedGold.get(itemId);
        if (owner == null) return;

        if (!owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
        } else {
            droppedGold.remove(itemId);
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        droppedGold.remove(event.getEntity().getUniqueId());
    }
}
