package me.kaistudio;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class InsuranceCommand {

    private final AnnouncePlugin plugin;

    public InsuranceCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("insurance")
            .then(Commands.literal("bronze").executes(ctx -> handleSubscribe(ctx, "bronze")))
            .then(Commands.literal("silver").executes(ctx -> handleSubscribe(ctx, "silver")))
            .then(Commands.literal("gold").executes(ctx -> handleSubscribe(ctx, "gold")))
            .then(Commands.literal("confirm")
                .then(Commands.argument("tier", StringArgumentType.word())
                    .executes(this::handleConfirm)))
            .then(Commands.literal("decline").executes(this::handleDecline))
            .then(Commands.literal("cancel").executes(this::handleCancel))
            .then(Commands.literal("status").executes(this::handleStatus))
            .then(Commands.literal("set")
                .requires(src -> src.getSender().hasPermission("kai.insurance"))
                .then(Commands.argument("tier", StringArgumentType.word())
                    .then(Commands.argument("deathpenalty", DoubleArgumentType.doubleArg(0, 100))
                        .then(Commands.argument("dailycost", DoubleArgumentType.doubleArg(0, 100))
                            .executes(this::handleSet)))))
            .then(Commands.literal("on")
                .requires(src -> src.getSender().hasPermission("kai.insurance"))
                .executes(ctx -> handleToggle(ctx, true)))
            .then(Commands.literal("off")
                .requires(src -> src.getSender().hasPermission("kai.insurance"))
                .executes(ctx -> handleToggle(ctx, false)))
            .build();
    }

    private int handleSubscribe(CommandContext<CommandSourceStack> ctx, String tier) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
        InsuranceManager im = plugin.getInsuranceManager();

        if (!im.isEnabled()) {
            sender.sendMessage(Component.text("Insurance Inc is currently offline.", NamedTextColor.RED));
            return 0;
        }

        String currentTier = im.getPlayerTier(sender.getUniqueId());
        if (tier.equals(currentTier)) {
            sender.sendMessage(Component.text("You are already on " + InsuranceManager.cap(tier) + " insurance.", NamedTextColor.YELLOW));
            return 0;
        }

        int totalNuggets = plugin.getDeathStateManager().getTotalNuggets(sender);
        int costNuggets = (int) Math.floor(totalNuggets * im.getDailyCostRate(tier));
        int deathPct = (int) Math.round(im.getDeathRate(tier) * 100);

        Component acceptBtn = Component.text("[Accept]", NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand("/insurance confirm " + tier));
        Component declineBtn = Component.text("[Decline]", NamedTextColor.RED)
            .clickEvent(ClickEvent.runCommand("/insurance decline"));

        if (currentTier == null) {
            sender.sendMessage(
                Component.text("Insurance Inc: " + InsuranceManager.cap(tier) + " (" + deathPct + "% death penalty) — "
                    + GoldUtil.format(costNuggets) + "/cycle. Charged immediately. ")
                    .append(acceptBtn).append(Component.text(" ")).append(declineBtn));
        } else {
            long mins = im.getMinutesUntilNextPayment(sender.getUniqueId());
            String action = im.isTierHigher(tier, currentTier) ? "Upgrade" : "Downgrade";
            sender.sendMessage(
                Component.text("Insurance Inc: " + action + " to " + InsuranceManager.cap(tier) + " (" + deathPct
                    + "% death penalty) in ~" + mins + " min — new cost " + GoldUtil.format(costNuggets) + "/cycle. ")
                    .append(acceptBtn).append(Component.text(" ")).append(declineBtn));
        }

        im.queuePendingConfirm(sender.getUniqueId(), tier);
        return 1;
    }

    private int handleConfirm(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
        InsuranceManager im = plugin.getInsuranceManager();

        String tier = StringArgumentType.getString(ctx, "tier");
        String pending = im.consumePendingConfirm(sender.getUniqueId());
        if (pending == null || !pending.equals(tier)) {
            sender.sendMessage(Component.text("No pending insurance confirmation.", NamedTextColor.RED));
            return 0;
        }

        String currentTier = im.getPlayerTier(sender.getUniqueId());
        if (currentTier == null) {
            im.subscribe(sender, tier);
        } else {
            im.setPendingTier(sender, tier);
            long mins = im.getMinutesUntilNextPayment(sender.getUniqueId());
            String action = im.isTierHigher(tier, currentTier) ? "upgrade" : "downgrade";
            sender.sendMessage(Component.text(
                "Insurance Inc: Your " + InsuranceManager.cap(currentTier) + " insurance will " + action
                    + " to " + InsuranceManager.cap(tier) + " in ~" + mins + " minute(s).",
                NamedTextColor.YELLOW));
        }
        return 1;
    }

    private int handleDecline(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
        plugin.getInsuranceManager().clearPendingConfirm(sender.getUniqueId());
        sender.sendMessage(Component.text("Insurance Inc: Request declined.", NamedTextColor.GRAY));
        return 1;
    }

    private int handleCancel(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
        InsuranceManager im = plugin.getInsuranceManager();

        String currentTier = im.getPlayerTier(sender.getUniqueId());
        if (currentTier == null) {
            sender.sendMessage(Component.text("You don't have active insurance.", NamedTextColor.RED));
            return 0;
        }

        im.setPendingTier(sender, "cancel");
        long mins = im.getMinutesUntilNextPayment(sender.getUniqueId());
        sender.sendMessage(Component.text(
            "Insurance Inc: Your " + InsuranceManager.cap(currentTier) + " insurance will cancel in ~" + mins + " minute(s).",
            NamedTextColor.YELLOW));
        return 1;
    }

    private int handleStatus(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
        InsuranceManager im = plugin.getInsuranceManager();

        String tier = im.getPlayerTier(sender.getUniqueId());
        String pending = im.getPlayerPendingTier(sender.getUniqueId());

        if (tier == null) {
            int basePct = (int) Math.round(im.getBaseDeathPenalty() * 100);
            sender.sendMessage(Component.text(
                "Insurance Inc: No active insurance. Death penalty: " + basePct + "%.", NamedTextColor.GRAY));
            return 1;
        }

        int deathPct = (int) Math.round(im.getDeathRate(tier) * 100);
        int totalNuggets = plugin.getDeathStateManager().getTotalNuggets(sender);
        int nextCost = (int) Math.floor(totalNuggets * im.getDailyCostRate(tier));
        long mins = im.getMinutesUntilNextPayment(sender.getUniqueId());

        sender.sendMessage(Component.text(
            "Insurance Inc: " + InsuranceManager.cap(tier) + " — " + deathPct + "% death penalty. "
                + "Next charge: " + GoldUtil.format(nextCost) + " in ~" + mins + " min.",
            NamedTextColor.GRAY));

        if (pending != null) {
            String pendingDisplay = "cancel".equals(pending) ? "cancellation" : InsuranceManager.cap(pending);
            sender.sendMessage(Component.text(
                "Insurance Inc: Pending change — " + pendingDisplay + " in ~" + mins + " min.", NamedTextColor.YELLOW));
        }
        return 1;
    }

    private int handleSet(CommandContext<CommandSourceStack> ctx) {
        if (!ctx.getSource().getSender().hasPermission("kai.insurance")) {
            ctx.getSource().getSender().sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return 0;
        }
        String tier = StringArgumentType.getString(ctx, "tier");
        if (!plugin.getInsuranceManager().isValidTier(tier)) {
            ctx.getSource().getSender().sendMessage(Component.text("Invalid tier. Use: bronze, silver, gold", NamedTextColor.RED));
            return 0;
        }
        double deathPct = DoubleArgumentType.getDouble(ctx, "deathpenalty") / 100.0;
        double dailyPct = DoubleArgumentType.getDouble(ctx, "dailycost") / 100.0;
        plugin.getInsuranceManager().setTierRates(tier, deathPct, dailyPct);
        ctx.getSource().getSender().sendMessage(Component.text(
            "Updated " + InsuranceManager.cap(tier) + ": " + (int) Math.round(deathPct * 100)
                + "% death penalty, " + (dailyPct * 100) + "% daily cost.",
            NamedTextColor.GREEN));
        return 1;
    }

    private int handleToggle(CommandContext<CommandSourceStack> ctx, boolean on) {
        if (!ctx.getSource().getSender().hasPermission("kai.insurance")) {
            ctx.getSource().getSender().sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return 0;
        }
        plugin.getInsuranceManager().setEnabled(on);
        if (!on) {
            Bukkit.broadcast(Component.text("Insurance Inc is shutting down for 24 hours", NamedTextColor.GOLD));
        } else {
            ctx.getSource().getSender().sendMessage(Component.text("Insurance Inc is now online.", NamedTextColor.GREEN));
        }
        return 1;
    }
}
