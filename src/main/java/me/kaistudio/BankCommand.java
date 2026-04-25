package me.kaistudio;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public class BankCommand {

    private final AnnouncePlugin plugin;

    public BankCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("bank")
            .then(Commands.literal("reset")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) {
                        sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                        return 0;
                    }
                    plugin.getMarketManager().resetMarket();
                    sender.sendMessage(Component.text("Market reset. All prices are back to 0%.", NamedTextColor.GREEN));
                    return 1;
                }))
            .then(Commands.literal("debug")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    MarketManager m = plugin.getMarketManager();
                    int count = m.getPriceCount();
                    sender.sendMessage(Component.text("[Market] Prices loaded: " + count, NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("[Market] IRON_INGOT listed: " + m.isListed(org.bukkit.Material.IRON_INGOT), NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("[Market] DIAMOND listed: " + m.isListed(org.bukkit.Material.DIAMOND), NamedTextColor.YELLOW));
                    java.io.File f = new java.io.File(plugin.getDataFolder(), "market_prices.yml");
                    sender.sendMessage(Component.text("[Market] prices file exists: " + f.exists() + " size: " + f.length() + "b", NamedTextColor.YELLOW));
                    return 1;
                }))
            .then(Commands.literal("leaderboard")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    List<Map.Entry<Material, Double>> top = plugin.getMarketManager().getTopDepressedItems(10);

                    sender.sendMessage(Component.text("━━ Market Leaderboard ━━", NamedTextColor.GOLD));

                    if (top.isEmpty()) {
                        sender.sendMessage(Component.text("No items have moved from base price yet.", NamedTextColor.GRAY));
                        return 1;
                    }

                    for (int i = 0; i < top.size(); i++) {
                        Material mat = top.get(i).getKey();
                        double mult  = top.get(i).getValue();
                        int pctChange = (int) Math.round((mult - 1.0) * 100);

                        MarketManager.PriceEntry base    = plugin.getMarketManager().getBaseEntry(mat);
                        int currentPayout = plugin.getMarketManager().calculatePayout(mat, base.qty());

                        NamedTextColor pctColor = pctChange >= -20 ? NamedTextColor.YELLOW : NamedTextColor.RED;

                        sender.sendMessage(
                            Component.text((i + 1) + ". ", NamedTextColor.GRAY)
                                .append(Component.text(MarketManager.formatMaterial(mat), NamedTextColor.WHITE))
                                .append(Component.text("  " + pctChange + "%", pctColor))
                                .append(Component.text("  (" + base.qty() + "x = "
                                    + MarketManager.formatNuggets(currentPayout) + ")", NamedTextColor.GRAY))
                        );
                    }
                    return 1;
                }))
            .build();
    }
}
