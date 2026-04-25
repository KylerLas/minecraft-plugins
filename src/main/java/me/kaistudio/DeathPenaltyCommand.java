package me.kaistudio;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class DeathPenaltyCommand {

    private final AnnouncePlugin plugin;

    public DeathPenaltyCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("deathpenalty")
            .requires(src -> src.getSender().hasPermission("kai.insurance"))
            .then(Commands.argument("percentage", DoubleArgumentType.doubleArg(0, 100))
                .executes(ctx -> {
                    if (!ctx.getSource().getSender().hasPermission("kai.insurance")) {
                        ctx.getSource().getSender().sendMessage(Component.text("No permission.", NamedTextColor.RED));
                        return 0;
                    }
                    double pct = DoubleArgumentType.getDouble(ctx, "percentage") / 100.0;
                    plugin.getInsuranceManager().setBaseDeathPenalty(pct);
                    ctx.getSource().getSender().sendMessage(Component.text(
                        "Base death penalty set to " + (int) Math.round(pct * 100) + "%.",
                        NamedTextColor.GREEN));
                    return 1;
                }))
            .build();
    }
}
