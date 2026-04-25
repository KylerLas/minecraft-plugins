package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class MarketTellerListener implements Listener {

    private final AnnouncePlugin plugin;

    public MarketTellerListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getMarketManager().isTeller(event.getRightClicked())) return;

        event.setCancelled(true); // prevent trade GUI from opening

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.AIR) {
            player.sendMessage(Component.text("Hold the item you want to sell in your main hand.", NamedTextColor.YELLOW));
            return;
        }

        int payout = plugin.getMarketManager().processSale(player, hand);

        if (payout < 0) {
            player.sendMessage(Component.text("That item cannot be sold at the bank.", NamedTextColor.RED));
            return;
        }

        MarketManager market = plugin.getMarketManager();
        int pct = (int)(market.getMultiplier(hand.getType()) * 100);
        String marketNote = pct < 100 ? " §7(market: " + pct + "%)" : "";

        player.sendMessage(Component.text(
            "Sold " + hand.getAmount() + "x " + MarketManager.formatMaterial(hand.getType())
            + " for " + MarketManager.formatNuggets(payout) + "." + marketNote,
            NamedTextColor.GREEN));
    }
}
