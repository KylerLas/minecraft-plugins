package me.kaistudio;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class StatisticsCommand {

    private final AnnouncePlugin plugin;

    public StatisticsCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("statistics")
            .then(Commands.literal("reset")
                .requires(src -> src.getSender().isOp())
                .executes(ctx -> {
                    if (!ctx.getSource().getSender().isOp()) {
                        ctx.getSource().getSender().sendMessage(Component.text("No permission.", NamedTextColor.RED));
                        return 0;
                    }
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getDatabaseManager().resetAllPlayerStats();
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                            ctx.getSource().getSender().sendMessage(Component.text(
                                "All player statistics have been reset.", NamedTextColor.GREEN)));
                    });
                    return 1;
                }))
            .build();
    }
}
