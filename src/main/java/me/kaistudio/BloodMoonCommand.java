package me.kaistudio;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class BloodMoonCommand {

    private final AnnouncePlugin plugin;

    public BloodMoonCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("bloodmoon")
            .then(Commands.literal("begin")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    BloodMoonManager bm = plugin.getBloodMoonManager();
                    if (bm.isBloodMoonActive()) {
                        sender.sendMessage(Component.text("A blood moon is already active.", NamedTextColor.RED));
                        return 0;
                    }
                    bm.beginBloodMoon();
                    return 1;
                })
                .then(Commands.literal("delay")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) { noPerms(sender); return 0; }
                        BloodMoonManager bm = plugin.getBloodMoonManager();
                        if (bm.isBloodMoonActive()) {
                            sender.sendMessage(Component.text("A blood moon is already active. Use /bloodmoon end first.", NamedTextColor.RED));
                            return 0;
                        }
                        bm.beginDelay();
                        sender.sendMessage(Component.text("10-minute blood moon countdown started.", NamedTextColor.DARK_RED));
                        return 1;
                    })))
            .then(Commands.literal("end")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    BloodMoonManager bm = plugin.getBloodMoonManager();
                    boolean wasActive = bm.isBloodMoonActive();
                    bm.forceEnd();
                    sender.sendMessage(Component.text(
                        wasActive ? "Blood moon ended." : "Blood moon countdown cleared. Next rescheduled.",
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
                            plugin.getBloodMoonManager().setIntervalAndDuration(interval, duration);
                            sender.sendMessage(Component.text(
                                "Blood moon: every " + interval + " min, lasts " + duration + " min.",
                                NamedTextColor.GREEN));
                            return 1;
                        }))))
            .then(Commands.literal("delay")
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) { noPerms(sender); return 0; }
                        BloodMoonManager bm = plugin.getBloodMoonManager();
                        if (bm.isBloodMoonActive()) {
                            sender.sendMessage(Component.text("Cannot delay during an active blood moon.", NamedTextColor.RED));
                            return 0;
                        }
                        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                        bm.addDelay(minutes);
                        org.bukkit.Bukkit.broadcast(Component.text(
                            "Blood Moon delayed by " + minutes + " minute" + (minutes == 1 ? "" : "s")
                            + ". Next blood moon in " + formatTime(bm.getSecondsToNext()) + ".",
                            NamedTextColor.DARK_RED));
                        return 1;
                    })))
            .then(Commands.literal("notify")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) { noPerms(sender); return 0; }
                        plugin.getBloodMoonManager().setNotificationsEnabled(true);
                        sender.sendMessage(Component.text("Blood moon notifications enabled.", NamedTextColor.GREEN));
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        if (!sender.isOp()) { noPerms(sender); return 0; }
                        plugin.getBloodMoonManager().setNotificationsEnabled(false);
                        sender.sendMessage(Component.text("Blood moon notifications disabled.", NamedTextColor.GREEN));
                        return 1;
                    })))
            .then(Commands.literal("time")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) { noPerms(sender); return 0; }
                    BloodMoonManager bm = plugin.getBloodMoonManager();
                    if (bm.isBloodMoonActive()) {
                        sender.sendMessage(Component.text(
                            "Blood moon active — " + formatTime(bm.getRemainingSeconds()) + " remaining.",
                            NamedTextColor.DARK_RED));
                    } else {
                        sender.sendMessage(Component.text(
                            "Next blood moon in " + formatTime(bm.getSecondsToNext()) + ".",
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
