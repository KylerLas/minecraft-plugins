package me.kaistudio;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PriceCommand {

    private final AnnouncePlugin plugin;

    public PriceCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("price")
            .executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                    return 0;
                }

                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    player.sendMessage(Component.text("Hold an item to check its market price.", NamedTextColor.YELLOW));
                    return 0;
                }

                Material mat = hand.getType();
                MarketManager market = plugin.getMarketManager();

                if (!market.isListed(mat)) {
                    player.sendMessage(Component.text(
                        MarketManager.formatMaterial(mat) + " cannot be sold at the bank.", NamedTextColor.RED));
                    return 1;
                }

                MarketManager.PriceEntry base = market.getBaseEntry(mat);
                double multiplier = market.getMultiplier(mat);
                int pct = (int)(multiplier * 100);

                // Current payout for the base quantity at current market rate
                int currentPayout = market.calculatePayout(mat, base.qty());

                NamedTextColor priceColor = pct >= 100 ? NamedTextColor.GREEN
                    : pct <= 30 ? NamedTextColor.RED : NamedTextColor.YELLOW;

                String baseStr    = MarketManager.formatNuggets(base.nuggets());
                String currentStr = MarketManager.formatNuggets(currentPayout);
                String qtyLabel   = base.qty() + "x " + MarketManager.formatMaterial(mat);

                Component msg = Component.text(qtyLabel + " — ", NamedTextColor.GOLD)
                    .append(Component.text(currentStr, priceColor));

                if (pct < 100) {
                    msg = msg.append(Component.text(
                        "  (base: " + baseStr + " | market: " + pct + "%)", NamedTextColor.GRAY));
                }

                player.sendMessage(msg);
                return 1;
            })
            .build();
    }
}
