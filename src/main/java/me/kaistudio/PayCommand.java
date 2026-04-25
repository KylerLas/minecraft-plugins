package me.kaistudio;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

public class PayCommand {

    private final AnnouncePlugin plugin;

    public PayCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pay")
            .then(Commands.literal("death")
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player sender)) {
                        ctx.getSource().getSender().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                        return 0;
                    }
                    if (!plugin.getDeathStateManager().isDead(sender.getUniqueId())) {
                        sender.sendMessage(Component.text("You are not in ghost state.", NamedTextColor.RED));
                        return 0;
                    }

                    int total = plugin.getDeathStateManager().getTotalNuggets(sender);
                    int debt = (int) Math.floor(total * plugin.getDeathStateManager().getDeathTaxRate(sender.getUniqueId()));

                    if (debt == 0) {
                        plugin.getDeathStateManager().exitDeathState(sender);
                        sender.sendMessage(Component.text("You have been revived. No gold to pay.", NamedTextColor.GREEN));
                        return 1;
                    }

                    int collected = plugin.getDeathStateManager().collectGold(sender, debt);
                    if (collected > debt) {
                        GoldUtil.addGold(sender, collected - debt);
                    }

                    plugin.getDeathStateManager().exitDeathState(sender);
                    sender.sendMessage(Component.text(
                        "You have been revived. " + GoldUtil.format(debt) + " was taken as a death penalty.",
                        NamedTextColor.GREEN));
                    return 1;
                })
            )
            .then(Commands.argument("player", ArgumentTypes.player())
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .executes(ctx -> doPay(ctx, 9))
                    .then(Commands.literal("nugget")
                        .executes(ctx -> doPay(ctx, 1)))
                    .then(Commands.literal("ingot")
                        .executes(ctx -> doPay(ctx, 9)))
                    .then(Commands.literal("block")
                        .executes(ctx -> doPay(ctx, 81)))
                )
            )
            .build();
    }

    // nuggetsPerUnit: 1 = nugget, 9 = ingot/default, 81 = block
    private int doPay(CommandContext<CommandSourceStack> ctx, int nuggetsPerUnit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(ctx.getSource().getSender() instanceof Player sender)) {
            ctx.getSource().getSender().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return 0;
        }

        List<Player> targets = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
            .resolve(ctx.getSource());

        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return 0;
        }

        Player target = targets.get(0);

        if (target.equals(sender)) {
            sender.sendMessage(Component.text("You cannot pay yourself.", NamedTextColor.RED));
            return 0;
        }

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int nuggets = amount * nuggetsPerUnit;
        int senderNuggets = GoldUtil.countNuggets(sender.getInventory());

        if (senderNuggets < nuggets) {
            sender.sendMessage(Component.text(
                "Insufficient gold. You have " + GoldUtil.format(senderNuggets) + " in your inventory.",
                NamedTextColor.RED));
            return 0;
        }

        GoldUtil.removeGold(sender, nuggets);
        GoldUtil.addGold(target, nuggets);

        String unit = switch (nuggetsPerUnit) {
            case 1  -> amount == 1 ? "nugget" : "nuggets";
            case 81 -> amount == 1 ? "gold block" : "gold blocks";
            default -> GoldUtil.format(nuggets); // ingot/default — show as "X gold"
        };
        String amountStr = nuggetsPerUnit == 9 ? unit : amount + " " + unit;

        sender.sendMessage(Component.text("Paid " + amountStr + " to " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text(sender.getName() + " paid you " + amountStr + ".", NamedTextColor.GREEN));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().incrementTransactionsSent(sender.getUniqueId().toString());
            plugin.getDatabaseManager().incrementTransactionsReceived(target.getUniqueId().toString());
        });

        return 1;
    }
}
