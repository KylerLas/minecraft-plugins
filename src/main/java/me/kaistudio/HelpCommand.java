package me.kaistudio;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class HelpCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("help")
            .executes(ctx -> {
                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;

                player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
                player.sendMessage(Component.text("  Command Reference", NamedTextColor.GOLD));
                player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

                header(player, "Economy");
                cmd(player, "/pay <player> <amount>",              "Pay gold ingots to a player");
                cmd(player, "/pay <player> <amount> nugget",       "Pay in nuggets");
                cmd(player, "/pay <player> <amount> ingot",        "Pay in ingots (same as default)");
                cmd(player, "/pay <player> <amount> block",        "Pay in gold blocks");
                cmd(player, "/request <player> <amount>",          "Request gold from a player");
                cmd(player, "/requests received",                  "View and accept incoming requests");
                cmd(player, "/requests sent",                      "View and cancel your sent requests");
                cmd(player, "/goldscore",                          "Show the full gold leaderboard");

                header(player, "Purgatory");
                cmd(player, "/pay death",                          "Pay your death tax to leave ghost state");

                header(player, "Insurance — India Insures You");
                cmd(player, "/insurance bronze",                   "Subscribe to Bronze (35% death penalty)");
                cmd(player, "/insurance silver",                   "Subscribe to Silver (20% death penalty)");
                cmd(player, "/insurance gold",                     "Subscribe to Gold (8% death penalty)");
                cmd(player, "/insurance cancel",                   "Cancel your policy at end of current cycle");
                cmd(player, "/insurance status",                   "View your active policy and next invoice");

                header(player, "Bank & Market");
                cmd(player, "/price",                              "Check the current sell price of your held item");

                player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
                return 1;
            })
            .build();
    }

    private void header(Player player, String title) {
        player.sendMessage(Component.text(" "));
        player.sendMessage(Component.text("  ◆ " + title, NamedTextColor.YELLOW));
    }

    private void cmd(Player player, String syntax, String description) {
        player.sendMessage(
            Component.text("  ")
                .append(Component.text(syntax, NamedTextColor.WHITE))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(description, NamedTextColor.GRAY))
        );
    }
}
