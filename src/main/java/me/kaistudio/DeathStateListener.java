package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.player.*;

public class DeathStateListener implements Listener {

    private final AnnouncePlugin plugin;

    public DeathStateListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (plugin.getDeathStateManager().isDead(event.getPlayer().getUniqueId())) return;

        Player player = event.getPlayer();
        boolean pvpDeath = player.getKiller() != null;

        // Always track gold drops as owned — only the victim can pick them up regardless of death type
        List<ItemStack> goldDrops = new ArrayList<>();
        event.getDrops().removeIf(stack -> {
            if (stack != null && GoldUtil.isTrackedGold(stack.getType())) {
                goldDrops.add(stack);
                return true;
            }
            return false;
        });
        for (ItemStack gold : goldDrops) {
            Item item = player.getWorld().dropItemNaturally(player.getLocation(), gold);
            plugin.getGoldDropListener().track(item, player.getUniqueId());
        }

        // Purgatory only applies to non-PvP deaths
        if (!pvpDeath) {
            plugin.getDeathStateManager().markPendingDeath(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDeathStateManager().consumePendingDeath(player.getUniqueId())) return;
        // 1-tick delay so the player is placed at their respawn location before we spawn the skull
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getDeathStateManager().enterDeathState(player);
            player.sendMessage(Component.text(
                "You are in ghost state. Type /pay death to be revived.", NamedTextColor.GRAY));
        }, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDeathStateManager().isDead(player.getUniqueId())) return;
        // 1-tick delay so the player is fully loaded into the world before spawning skull
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getDeathStateManager().spawnSkull(player);
            player.sendMessage(Component.text(
                "You are still in ghost state. Type /pay death to be revived.", NamedTextColor.GRAY));
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Clear pending flag if they disconnect on the death screen before respawning
        plugin.getDeathStateManager().clearPendingDeath(player.getUniqueId());
        // Despawn the skull entity but leave the UUID in YAML so ghost state persists on reconnect
        if (plugin.getDeathStateManager().isDead(player.getUniqueId())) {
            plugin.getDeathStateManager().despawnSkull(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDeathStateManager().isDead(player.getUniqueId())) {
            plugin.getDeathStateManager().updateSkullPosition(player);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getDeathStateManager().isDead(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getDeathStateManager().isDead(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (plugin.getDeathStateManager().isDead(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getDeathStateManager().isDead(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Ghost players cannot deal damage
        if (event.getDamager() instanceof Player p
                && plugin.getDeathStateManager().isDead(p.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        // Ghost players cannot receive damage from other entities
        if (event.getEntity() instanceof Player p
                && plugin.getDeathStateManager().isDead(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Cancel environmental damage (fall, fire, lava, etc.) to ghost players
        if (event instanceof EntityDamageByEntityEvent) return; // handled above
        if (event.getEntity() instanceof Player p
                && plugin.getDeathStateManager().isDead(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (plugin.getDeathStateManager().isDead(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
