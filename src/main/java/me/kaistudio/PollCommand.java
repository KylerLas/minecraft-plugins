package me.kaistudio;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PollCommand {

    private final AnnouncePlugin plugin;

    public PollCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("poll")
            .then(Commands.literal("yes")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Only players can vote.", NamedTextColor.RED));
                        return 0;
                    }
                    plugin.getPollManager().vote(player, true);
                    return 1;
                }))
            .then(Commands.literal("no")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Only players can vote.", NamedTextColor.RED));
                        return 0;
                    }
                    plugin.getPollManager().vote(player, false);
                    return 1;
                }))
            .then(Commands.literal("end")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    PollManager pm = plugin.getPollManager();
                    if (!pm.hasActivePoll()) {
                        sender.sendMessage(Component.text("No active poll.", NamedTextColor.RED));
                        return 0;
                    }
                    pm.endPoll();
                    return 1;
                }))
            .then(Commands.argument("question", StringArgumentType.greedyString())
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    String question = StringArgumentType.getString(ctx, "question");
                    plugin.getPollManager().startPoll(question);
                    return 1;
                }))
            .build();
    }

    private static void noPerms(CommandSender sender) {
        sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
    }
}
