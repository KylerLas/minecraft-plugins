package me.kaistudio;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // Register a gold item entity as owned by a player (used for death drops)
    public void track(Item item, UUID playerUuid) {
        droppedGold.put(item.getUniqueId(), playerUuid);
    }

    // Count nuggets in all floor items owned by this player
    public int countNuggets(UUID playerUuid) {
        int nuggets = 0;
        for (Map.Entry<UUID, UUID> entry : droppedGold.entrySet()) {
            if (!entry.getValue().equals(playerUuid)) continue;
            // Resolve the entity from all worlds
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                org.bukkit.entity.Entity entity = world.getEntity(entry.getKey());
                if (entity instanceof Item item) {
                    nuggets += nuggetValue(item.getItemStack());
                    break;
                }
            }
        }
        return nuggets;
    }

    // Remove up to maxNuggets from floor items owned by this player, return amount collected
    public int collect(UUID playerUuid, int maxNuggets) {
        if (maxNuggets <= 0) return 0;
        int collected = 0;

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : droppedGold.entrySet()) {
            if (collected >= maxNuggets) break;
            if (!entry.getValue().equals(playerUuid)) continue;

            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                org.bukkit.entity.Entity entity = world.getEntity(entry.getKey());
                if (!(entity instanceof Item item)) break;

                ItemStack stack = item.getItemStack();
                int perItem = nuggetValue(stack) / stack.getAmount();
                if (perItem == 0) break;

                int stillNeeded = maxNuggets - collected;
                int take = (int) Math.ceil((double) stillNeeded / perItem);
                take = Math.min(take, stack.getAmount());

                collected += take * perItem;
                if (take >= stack.getAmount()) {
                    item.remove();
                    toRemove.add(entry.getKey());
                } else {
                    stack.setAmount(stack.getAmount() - take);
                    item.setItemStack(stack);
                }
                break;
            }
        }
        toRemove.forEach(droppedGold::remove);
        return collected;
    }

    private int nuggetValue(ItemStack stack) {
        if (stack == null) return 0;
        return switch (stack.getType()) {
            case GOLD_BLOCK  -> stack.getAmount() * 81;
            case GOLD_INGOT  -> stack.getAmount() * 9;
            case GOLD_NUGGET -> stack.getAmount();
            default -> 0;
        };
    }
}
