package me.kaistudio;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.regex.Pattern;

public class ViolationListener implements Listener {

    private static final Pattern PROFANITY = Pattern.compile(
        "\\b(fuck|fucking|fucked|fucker|shit|shitty|bitch|bitches|bastard|asshole|" +
        "nigger|nigga|cunt|piss|pissed|whore|slut|ass|dick)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final AnnouncePlugin plugin;

    public ViolationListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        // Bank teller takes priority over generic villager check
        if (plugin.getMarketManager().isTeller(entity)) {
            issueFine(killer, "Murder of a Bank Teller", 20);
            return;
        }

        // Court Officer — contempt of court
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Villager officer = (Villager) player.getWorld().spawnEntity(player.getLocation(), EntityType.VILLAGER);
            officer.setCustomName("Court Officer");
            officer.setCustomNameVisible(true);
            ((Mob) officer).setTarget(player);

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
            player.getWorld().dropItem(player.getLocation(), note);

            plugin.getDatabaseManager().logFine(
                player.getName(),
                player.getUniqueId().toString(),
                reason,
                amount
            );
        }, 100L);
    }
}
