package me.kaistudio;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class ChickenDeathListener implements Listener {

    private final AnnouncePlugin plugin;

    public ChickenDeathListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getType() != EntityType.CHICKEN) return;
        if (event.getEntity().getKiller() == null) return;

        Player killer = event.getEntity().getKiller();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Spawn a villager at the killer's location
            Villager npc = (Villager) killer.getWorld().spawnEntity(killer.getLocation(), EntityType.VILLAGER);
            npc.setCustomName("Court Officer");
            npc.setCustomNameVisible(true);
            ((Mob) npc).setTarget(killer);

            // Create the fine note
            ItemStack note = new ItemStack(Material.PAPER);
            ItemMeta meta = note.getItemMeta();
            meta.displayName(Component.text("Official Fine Notice", NamedTextColor.RED));
            meta.lore(List.of(
                Component.text("You have been fined 5 schmeckles", NamedTextColor.WHITE),
                Component.text("for the killing of King Cock the 3rd.", NamedTextColor.WHITE),
                Component.text("Pay up or face the consequences.", NamedTextColor.GRAY)
            ));
            note.setItemMeta(meta);

            // Drop the note at the killer's feet
            killer.getWorld().dropItem(killer.getLocation(), note);

            // Log the fine to Cosmos DB
            plugin.getDatabaseManager().logFine(
                killer.getName(),
                killer.getUniqueId().toString(),
                "Killing of King Cock the 3rd",
                5
            );
        }, 100L);
    }
}
