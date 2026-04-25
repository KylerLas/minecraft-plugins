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
            sender.sendMessage(Component.text("India Insures You is temporarily closed for restructuring. Deaths uninsured in the meantime.", NamedTextColor.RED));
            return 0;
        }

        String currentTier = im.getPlayerTier(sender.getUniqueId());
        if (tier.equals(currentTier)) {
            sender.sendMessage(Component.text("Our records show you are already enrolled in our " + InsuranceManager.cap(tier) + " Plan. Your continued loyalty is noted.", NamedTextColor.YELLOW));
            return 0;
        }

        int totalNuggets = plugin.getDeathStateManager().getTotalNuggets(sender);
        int costNuggets = (int) Math.floor(totalNuggets * im.getDailyCostRate(tier));
        int deathPct = (int) Math.round(im.getDeathRate(tier) * 100);

        Component acceptBtn = Component.text("[Enroll]", NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand("/insurance confirm " + tier));
        Component declineBtn = Component.text("[Decline]", NamedTextColor.RED)
            .clickEvent(ClickEvent.runCommand("/insurance decline"));

        if (currentTier == null) {
            sender.sendMessage(
                Component.text("India Insures You — " + InsuranceManager.cap(tier) + " Plan: only " + deathPct + "% surrendered on death. A bargain, frankly. Cost: "
                    + GoldUtil.format(costNuggets) + "/cycle. Charged immediately. ")
                    .append(acceptBtn).append(Component.text(" ")).append(declineBtn));
        } else {
            long mins = im.getMinutesUntilNextPayment(sender.getUniqueId());
            String action = im.isTierHigher(tier, currentTier) ? "Transitioning up" : "Transitioning down";
            sender.sendMessage(
                Component.text("India Insures You — " + action + " to " + InsuranceManager.cap(tier) + " Plan (" + deathPct
                    + "% death extraction) in ~" + mins + " min. New extraction rate: " + GoldUtil.format(costNuggets) + "/cycle. ")
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
            sender.sendMessage(Component.text("We find no pending enrollment on file. Please try again.", NamedTextColor.RED));
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
                "India Insures You: Your " + InsuranceManager.cap(currentTier) + " Plan will " + action
                    + " to " + InsuranceManager.cap(tier) + " in ~" + mins + " minute(s). We look forward to serving you.",
                NamedTextColor.YELLOW));
        }
        return 1;
    }

    private int handleDecline(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
        plugin.getInsuranceManager().clearPendingConfirm(sender.getUniqueId());
        sender.sendMessage(Component.text("Your loss. Literally.", NamedTextColor.GRAY));
        return 1;
    }

    private int handleCancel(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
        InsuranceManager im = plugin.getInsuranceManager();

        String currentTier = im.getPlayerTier(sender.getUniqueId());
        if (currentTier == null) {
            sender.sendMessage(Component.text("You are not currently enrolled. Full extraction applies upon death. Consider this a warning.", NamedTextColor.RED));
            return 0;
        }

        im.setPendingTier(sender, "cancel");
        long mins = im.getMinutesUntilNextPayment(sender.getUniqueId());
        sender.sendMessage(Component.text(
            "India Insures You: Your " + InsuranceManager.cap(currentTier) + " Plan will be terminated in ~" + mins + " minute(s). We're sorry to see you go.",
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
                "India Insures You: No active plan. " + basePct + "% extracted upon death. We suggest reconsidering.", NamedTextColor.GRAY));
            return 1;
        }

        int deathPct = (int) Math.round(im.getDeathRate(tier) * 100);
        int totalNuggets = plugin.getDeathStateManager().getTotalNuggets(sender);
        int nextCost = (int) Math.floor(totalNuggets * im.getDailyCostRate(tier));
        long mins = im.getMinutesUntilNextPayment(sender.getUniqueId());

        sender.sendMessage(Component.text(
            "India Insures You — " + InsuranceManager.cap(tier) + " Plan | " + deathPct + "% death extraction | "
                + "Next invoice: " + GoldUtil.format(nextCost) + " in ~" + mins + " min.",
            NamedTextColor.GRAY));

        if (pending != null) {
            String pendingDisplay = "cancel".equals(pending) ? "termination" : InsuranceManager.cap(pending) + " Plan";
            sender.sendMessage(Component.text(
                "Pending: " + pendingDisplay + " in ~" + mins + " min. We'll miss you. Your gold especially.", NamedTextColor.YELLOW));
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
            InsuranceManager.cap(tier) + " Plan rates updated. The board is pleased.",
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
            Bukkit.broadcast(Component.text("India Insures You is suspending operations for 24 hours. All deaths fully taxed in the interim.", NamedTextColor.GOLD));
        } else {
            Bukkit.broadcast(Component.text("India Insures You has resumed operations. Your assets are once again... protected.", NamedTextColor.GREEN));
        }
        return 1;
    }
}
