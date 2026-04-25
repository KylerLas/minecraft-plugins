package me.kaistudio;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GoldScoreCommand {

    private final AnnouncePlugin plugin;

    public GoldScoreCommand(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("goldscore")
            .executes(ctx -> {
                showLeaderboard(ctx.getSource().getSender());
                return 1;
            })
            .then(Commands.literal("off")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                        return 0;
                    }
                    plugin.setLeaderboardHidden(player.getUniqueId(), true);
                    player.setScoreboard(plugin.getBlankScoreboard());
                    player.sendMessage(Component.text("Gold leaderboard hidden. Use /goldscore on to show it again.", NamedTextColor.YELLOW));
                    return 1;
                }))
            .then(Commands.literal("on")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                        return 0;
                    }
                    plugin.setLeaderboardHidden(player.getUniqueId(), false);
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    player.sendMessage(Component.text("Gold leaderboard visible.", NamedTextColor.GREEN));
                    return 1;
                }))
            .build();
    }

    private void showLeaderboard(CommandSender sender) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective("gold_lb");

        if (obj == null || board.getEntries().isEmpty()) {
            sender.sendMessage(Component.text("No gold data available yet. Wait for the next scan.", NamedTextColor.GRAY));
            return;
        }

        List<Map.Entry<String, Integer>> sorted = board.getEntries().stream()
            .filter(entry -> {
                Score score = obj.getScore(entry);
                return score.isScoreSet();
            })
            .map(entry -> Map.entry(entry, obj.getScore(entry).getScore()))
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            sender.sendMessage(Component.text("No gold data available yet.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("━━━ Gold Leaderboard ━━━", NamedTextColor.GOLD));
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            NamedTextColor rankColor = i == 0 ? NamedTextColor.GOLD : i == 1 ? NamedTextColor.GRAY : i == 2 ? NamedTextColor.DARK_RED : NamedTextColor.WHITE;
            sender.sendMessage(
                Component.text("#" + (i + 1) + " ", rankColor)
                    .append(Component.text(entry.getKey(), NamedTextColor.WHITE))
                    .append(Component.text(" — " + entry.getValue() + " gold", NamedTextColor.YELLOW))
            );
        }
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }
}
