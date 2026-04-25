package me.kaistudio;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
            .then(Commands.literal("percentage")
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                    .then(Commands.argument("pct", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!sender.isOp()) {
                                sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                                return 0;
                            }
                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                            int pct = IntegerArgumentType.getInteger(ctx, "pct");
                            plugin.getMarketManager().setRecoveryParams(minutes, pct);
                            sender.sendMessage(Component.text(
                                "Recovery set: +" + pct + "% every " + minutes + " minute(s).", NamedTextColor.GREEN));
                            return 1;
                        }))))
            .then(Commands.literal("build")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) {
                            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                            return 0;
                        }
                        plugin.getMarketManager().setBuildModeEnabled(true);
                        sender.sendMessage(Component.text("Bank build mode ON — the zone can now be edited.", NamedTextColor.GREEN));
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) {
                            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                            return 0;
                        }
                        plugin.getMarketManager().setBuildModeEnabled(false);
                        sender.sendMessage(Component.text("Bank build mode OFF — the zone is protected.", NamedTextColor.RED));
                        return 1;
                    })))
            .then(Commands.literal("depercentage")
                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 500))
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) {
                            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                            return 0;
                        }
                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                        plugin.getMarketManager().setDepercentageMultiplier(amount);
                        sender.sendMessage(Component.text(
                            "Decay rate set to " + amount + "% of default.", NamedTextColor.GREEN));
                        return 1;
                    })))
            .build();
    }
}
