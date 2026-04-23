package me.kaistudio;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RequestCommand {

    private final AnnouncePlugin plugin;

    public RequestCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    // /request <player> <amount>
    public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> buildRequest() {
        return Commands.literal("request")
            .then(Commands.argument("player", ArgumentTypes.player())
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
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
                            sender.sendMessage(Component.text("You cannot request gold from yourself.", NamedTextColor.RED));
                            return 0;
                        }

                        int nuggets = IntegerArgumentType.getInteger(ctx, "amount") * 9;

                        Request req = plugin.getRequestManager().create(
                            sender.getUniqueId(), sender.getName(),
                            target.getUniqueId(), target.getName(),
                            nuggets
                        );

                        sender.sendMessage(Component.text(
                            "Request sent to " + target.getName() + " for " + GoldUtil.format(nuggets) + ".",
                            NamedTextColor.YELLOW));

                        notifyTarget(target, req);
                        return 1;
                    })
                )
            )
            .build();
    }

    // /requests sent|received|accept|decline|cancel
    public com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> buildRequests() {
        return Commands.literal("requests")
            .then(Commands.literal("sent").executes(ctx -> {
                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                showSent(player);
                return 1;
            }))
            .then(Commands.literal("received").executes(ctx -> {
                if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                showReceived(player);
                return 1;
            }))
            .then(Commands.literal("accept")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        handleAccept(player, StringArgumentType.getString(ctx, "id"));
                        return 1;
                    })))
            .then(Commands.literal("decline")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        handleDecline(player, StringArgumentType.getString(ctx, "id"));
                        return 1;
                    })))
            .then(Commands.literal("cancel")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) return 0;
                        handleCancel(player, StringArgumentType.getString(ctx, "id"));
                        return 1;
                    })))
            .build();
    }

    private void showSent(Player player) {
        List<Request> sent = plugin.getRequestManager().getSent(player.getUniqueId());
        if (sent.isEmpty()) {
            player.sendMessage(Component.text("You have no pending sent requests.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("=== Sent Requests ===", NamedTextColor.GOLD));
        for (Request req : sent) {
            player.sendMessage(
                Component.text("To " + req.toName + ": " + GoldUtil.format(req.nuggets), NamedTextColor.WHITE)
                    .append(Component.text(" [Cancel]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/requests cancel " + req.id)))
            );
        }
    }

    private void showReceived(Player player) {
        List<Request> received = plugin.getRequestManager().getReceived(player.getUniqueId());
        if (received.isEmpty()) {
            player.sendMessage(Component.text("You have no pending requests.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("=== Incoming Requests ===", NamedTextColor.GOLD));
        for (Request req : received) {
            player.sendMessage(
                Component.text(req.fromName + " wants " + GoldUtil.format(req.nuggets) + " from you", NamedTextColor.WHITE)
                    .append(Component.text(" [Accept]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/requests accept " + req.id)))
                    .append(Component.text(" [Decline]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/requests decline " + req.id)))
            );
        }
    }

    private void handleAccept(Player player, String idStr) {
        Optional<Request> opt = parseAndValidate(player, idStr, player.getUniqueId(), true);
        if (opt.isEmpty()) return;
        Request req = opt.get();

        int playerNuggets = GoldUtil.countNuggets(player.getInventory());
        if (playerNuggets < req.nuggets) {
            player.sendMessage(Component.text(
                "Insufficient gold. You have " + GoldUtil.format(playerNuggets) + " in your inventory. Request remains pending.",
                NamedTextColor.RED));
            return;
        }

        Player requester = plugin.getServer().getPlayer(req.fromUUID);
        if (requester == null || !requester.isOnline()) {
            player.sendMessage(Component.text(req.fromName + " is no longer online. Request cancelled.", NamedTextColor.YELLOW));
            plugin.getRequestManager().remove(req.id);
            return;
        }

        GoldUtil.removeGold(player, req.nuggets);
        GoldUtil.addGold(requester, req.nuggets);

        player.sendMessage(Component.text("Sent " + GoldUtil.format(req.nuggets) + " to " + req.fromName + ".", NamedTextColor.GREEN));
        requester.sendMessage(Component.text(player.getName() + " accepted your request for " + GoldUtil.format(req.nuggets) + ".", NamedTextColor.GREEN));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDatabaseManager().incrementTransactionsSent(player.getUniqueId().toString());
            plugin.getDatabaseManager().incrementTransactionsReceived(req.fromUUID.toString());
        });

        plugin.getRequestManager().remove(req.id);
    }

    private void handleDecline(Player player, String idStr) {
        Optional<Request> opt = parseAndValidate(player, idStr, player.getUniqueId(), true);
        if (opt.isEmpty()) return;
        Request req = opt.get();

        plugin.getRequestManager().remove(req.id);
        player.sendMessage(Component.text("Request from " + req.fromName + " declined.", NamedTextColor.GRAY));

        Player requester = plugin.getServer().getPlayer(req.fromUUID);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(Component.text(player.getName() + " declined your gold request.", NamedTextColor.RED));
        }
    }

    private void handleCancel(Player player, String idStr) {
        Optional<Request> opt = parseAndValidate(player, idStr, player.getUniqueId(), false);
        if (opt.isEmpty()) return;
        Request req = opt.get();

        plugin.getRequestManager().remove(req.id);
        player.sendMessage(Component.text("Request to " + req.toName + " cancelled.", NamedTextColor.GRAY));

        Player target = plugin.getServer().getPlayer(req.toUUID);
        if (target != null && target.isOnline()) {
            target.sendMessage(Component.text(player.getName() + " cancelled their gold request.", NamedTextColor.GRAY));
        }
    }

    private Optional<Request> parseAndValidate(Player player, String idStr, UUID expectedUUID, boolean asRecipient) {
        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid request ID.", NamedTextColor.RED));
            return Optional.empty();
        }

        Optional<Request> opt = plugin.getRequestManager().get(id);
        if (opt.isEmpty()) {
            player.sendMessage(Component.text("Request not found or already resolved.", NamedTextColor.RED));
            return Optional.empty();
        }

        Request req = opt.get();
        UUID relevantUUID = asRecipient ? req.toUUID : req.fromUUID;
        if (!relevantUUID.equals(expectedUUID)) {
            player.sendMessage(Component.text("That is not your request.", NamedTextColor.RED));
            return Optional.empty();
        }

        return Optional.of(req);
    }

    public void notifyTarget(Player target, Request req) {
        target.sendMessage(
            Component.text(req.fromName + " is requesting " + GoldUtil.format(req.nuggets) + " from you.", NamedTextColor.YELLOW)
                .append(Component.text(" [Accept]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/requests accept " + req.id)))
                .append(Component.text(" [Decline]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/requests decline " + req.id)))
        );
        target.sendMessage(Component.text("Use /requests received to view all incoming requests.", NamedTextColor.GRAY));
    }
}
