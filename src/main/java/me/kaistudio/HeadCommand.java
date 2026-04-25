package me.kaistudio;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class HeadCommand {

    private final AnnouncePlugin plugin;

    public HeadCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("head")
            .then(Commands.literal("cleanup")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.isOp()) {
                        sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
                        return 0;
                    }
                    int removed = plugin.getDeathStateManager().cleanupSkulls();
                    sender.sendMessage(Component.text(
                        "Removed " + removed + " orphan skull" + (removed == 1 ? "" : "s") + " from the world.",
                        NamedTextColor.GREEN));
                    return 1;
                }))
            .build();
    }
}
