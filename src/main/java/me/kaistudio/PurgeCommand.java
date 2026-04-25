package me.kaistudio;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class PurgeCommand {

    private final AnnouncePlugin plugin;

    public PurgeCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("purge")
            .then(Commands.literal("begin")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    PurgeManager pm = plugin.getPurgeManager();
                    if (pm.isPurgeActive()) {
                        sender.sendMessage(Component.text("A purge is already active.", NamedTextColor.RED));
                        return 0;
                    }
                    pm.beginPurge();
                    return 1;
                })
                .then(Commands.literal("delay")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) { noPerms(sender); return 0; }
                        PurgeManager pm = plugin.getPurgeManager();
                        if (pm.isPurgeActive()) {
                            sender.sendMessage(Component.text("A purge is already active. Use /purge end first.", NamedTextColor.RED));
                            return 0;
                        }
                        pm.beginDelay();
                        sender.sendMessage(Component.text("10-minute purge countdown started.", NamedTextColor.YELLOW));
                        return 1;
                    })))
            .then(Commands.literal("end")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    PurgeManager pm = plugin.getPurgeManager();
                    boolean wasActive = pm.isPurgeActive();
                    pm.forceEnd();
                    sender.sendMessage(Component.text(
                        wasActive ? "Purge ended." : "Purge countdown cleared. Next purge rescheduled.",
                        NamedTextColor.YELLOW));
                    return 1;
                }))
            .then(Commands.literal("set")
                .then(Commands.argument("interval", IntegerArgumentType.integer(1))
                    .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (!sender.isOp()) { noPerms(sender); return 0; }
                            int interval = IntegerArgumentType.getInteger(ctx, "interval");
                            int duration = IntegerArgumentType.getInteger(ctx, "duration");
                            plugin.getPurgeManager().setIntervalAndDuration(interval, duration);
                            sender.sendMessage(Component.text(
                                "Purge: every " + interval + " min, lasts " + duration + " min.",
                                NamedTextColor.GREEN));
                            return 1;
                        }))))
            .then(Commands.literal("delay")
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) { noPerms(sender); return 0; }
                        PurgeManager pm = plugin.getPurgeManager();
                        if (pm.isPurgeActive()) {
                            sender.sendMessage(Component.text("Cannot delay during an active purge.", NamedTextColor.RED));
                            return 0;
                        }
                        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                        pm.addDelay(minutes);
                        org.bukkit.Bukkit.broadcast(Component.text(
                            "Purge delayed by " + minutes + " minute" + (minutes == 1 ? "" : "s")
                            + ". Next purge in " + formatTime(pm.getSecondsToNextPurge()) + ".",
                            NamedTextColor.YELLOW));
                        return 1;
                    })))
            .then(Commands.literal("reset")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    PurgeManager pm = plugin.getPurgeManager();
                    pm.reset();
                    org.bukkit.Bukkit.broadcast(Component.text(
                        "Purge timer reset. Next purge in " + formatTime(pm.getSecondsToNextPurge()) + ".",
                        NamedTextColor.YELLOW));
                    return 1;
                }))
            .then(Commands.literal("notify")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) { noPerms(sender); return 0; }
                        plugin.getPurgeManager().setNotificationsEnabled(true);
                        sender.sendMessage(Component.text("Purge notifications enabled.", NamedTextColor.GREEN));
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) { noPerms(sender); return 0; }
                        plugin.getPurgeManager().setNotificationsEnabled(false);
                        sender.sendMessage(Component.text("Purge notifications disabled.", NamedTextColor.GREEN));
                        return 1;
                    })))
            .then(Commands.literal("time")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    PurgeManager pm = plugin.getPurgeManager();
                    if (pm.isPurgeActive()) {
                        sender.sendMessage(Component.text(
                            "Purge active — " + formatTime(pm.getRemainingPurgeSeconds()) + " remaining.",
                            NamedTextColor.RED));
                    } else {
                        sender.sendMessage(Component.text(
                            "Next purge in " + formatTime(pm.getSecondsToNextPurge()) + ".",
                            NamedTextColor.YELLOW));
                    }
                    return 1;
                }))
            .build();
    }

    private static void noPerms(CommandSender sender) {
        sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
