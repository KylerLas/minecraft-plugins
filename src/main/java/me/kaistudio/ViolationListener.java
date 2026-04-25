package me.kaistudio;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public class ViolationListener implements Listener {

    private static final Pattern PROFANITY = Pattern.compile(
        "\\b(fuck|fucking|fucked|fucker|shit|shitty|bitch|bitches|bastard|asshole|" +
        "nigger|nigga|cunt|piss|pissed|whore|slut|ass|dick)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final int SPAWN_RADIUS    = 15;   // blocks away from player
    private static final long DESPAWN_TICKS  = 2400L; // 2 minutes
    private static final long CHECK_INTERVAL = 10L;   // re-target every 0.5s
    private static final double ARRIVE_DIST  = 2.5;   // drop note within this distance

    private final AnnouncePlugin plugin;
    private final Random random = new Random();

    public ViolationListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        if (plugin.getMarketManager().isTeller(entity)) {
            issueFine(killer, "Murder of a Bank Teller", 20);
            return;
        }

        if (entity instanceof Villager && "Court Officer".equals(entity.getCustomName())) {
            issueFine(killer, "Contempt of court — killing a Court Officer", 20);
            return;
        }

        record Fine(String reason, int amount) {}
        Fine fine = switch (entity.getType()) {
            case WOLF     -> new Fine("Unlawful killing of a protected wolf", 10);
            case TURTLE   -> new Fine("Destruction of an endangered sea turtle", 20);
            case PANDA    -> new Fine("Poaching of a protected panda", 30);
            case VILLAGER -> new Fine("Murder of a civilian", 25);
            case CAT      -> new Fine("Killing of a protected domestic cat", 10);
            case AXOLOTL  -> new Fine("Killing of an endangered axolotl", 15);
            case HORSE    -> new Fine("Destruction of a protected mount", 15);
            case DOLPHIN  -> new Fine("Killing of a protected marine mammal", 20);
            default       -> null;
        };
        if (fine == null) return;

        issueFine(killer, fine.reason(), fine.amount());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.TNT) return;
        issueFine(event.getPlayer(), "Placing of explosive materials in a public space", 20);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (PROFANITY.matcher(message).find()) {
            issueFine(event.getPlayer(), "Use of profanity in public communications", 15);
        }
    }

    private void issueFine(Player player, String reason, int amount) {
        // Log to DB immediately for accurate timestamp
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().logFine(
                player.getName(), player.getUniqueId().toString(), reason, amount));

        // Spawn officer after a brief delay so the event resolves first
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location spawnLoc = randomSurfaceLocation(player.getLocation(), SPAWN_RADIUS);

            Villager officer = player.getWorld().spawn(spawnLoc, Villager.class, v -> {
                v.setCustomName("Court Officer");
                v.setCustomNameVisible(true);
                v.setInvulnerable(true);
                v.setAI(true);
            });

            ItemStack note = buildNote(player, reason, amount);
            boolean[] noteDropped = {false};
            int[] taskId = {-1};

            // Re-target every 0.5s and drop note on arrival
            taskId[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!officer.isValid() || noteDropped[0]) {
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    return;
                }
                officer.getPathfinder().moveTo(player, 1.2);
                if (officer.getLocation().distanceSquared(player.getLocation()) <= ARRIVE_DIST * ARRIVE_DIST) {
                    player.getWorld().dropItem(player.getLocation(), note);
                    noteDropped[0] = true;
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                }
            }, 5L, CHECK_INTERVAL).getTaskId();

            // Despawn after 2 minutes, drop note at player's feet if never arrived
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Bukkit.getScheduler().cancelTask(taskId[0]);
                if (!noteDropped[0]) {
                    player.getWorld().dropItem(player.getLocation(), note);
                }
                if (officer.isValid()) officer.remove();
            }, DESPAWN_TICKS);

        }, 20L);
    }

    private ItemStack buildNote(Player player, String reason, int amount) {
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        meta.displayName(Component.text("Official Fine Notice", NamedTextColor.RED));
        meta.lore(List.of(
            Component.text("Issued to: " + player.getName(), NamedTextColor.YELLOW),
            Component.text("You have been fined " + amount + " gold", NamedTextColor.WHITE),
            Component.text("for: " + reason + ".", NamedTextColor.WHITE),
            Component.text("Pay up or face the consequences.", NamedTextColor.GRAY)
        ));
        note.setItemMeta(meta);
        return note;
    }

    private Location randomSurfaceLocation(Location center, int radius) {
        double angle = random.nextDouble() * 2 * Math.PI;
        int x = (int) (center.getX() + radius * Math.cos(angle));
        int z = (int) (center.getZ() + radius * Math.sin(angle));
        int y = center.getWorld().getHighestBlockYAt(x, z) + 1;
        return new Location(center.getWorld(), x + 0.5, y, z + 0.5);
    }
}
