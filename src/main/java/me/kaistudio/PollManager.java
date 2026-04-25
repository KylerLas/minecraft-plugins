package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class PollManager {

    private static final int DURATION_SECONDS = 60;

    private final AnnouncePlugin plugin;

    private String question = null;
    private final Set<UUID> yesVoters = new LinkedHashSet<>();
    private final Set<UUID> noVoters  = new LinkedHashSet<>();
    private BukkitTask expiryTask = null;

    public PollManager(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasActivePoll() { return question != null; }

    public void startPoll(String question) {
        if (hasActivePoll()) cancelSilently();
        this.question = question;
        yesVoters.clear();
        noVoters.clear();
        broadcastPollOpen();
        expiryTask = Bukkit.getScheduler().runTaskLater(plugin, this::endPoll, DURATION_SECONDS * 20L);
    }

    public void vote(Player player, boolean yes) {
        if (!hasActivePoll()) {
            player.sendMessage(Component.text("There is no active poll.", NamedTextColor.RED));
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean alreadySame = yes ? yesVoters.contains(uuid) : noVoters.contains(uuid);
        if (alreadySame) {
            player.sendMessage(Component.text("You already voted " + (yes ? "YES" : "NO") + ".", NamedTextColor.GRAY));
            return;
        }
        if (yes) { yesVoters.add(uuid); noVoters.remove(uuid); }
        else      { noVoters.add(uuid);  yesVoters.remove(uuid); }

        Bukkit.broadcast(
            Component.text(player.getName() + " voted ", NamedTextColor.GRAY)
                .append(Component.text(yes ? "YES" : "NO", yes ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD))
                .append(Component.text("  ·  YES: " + yesVoters.size() + "  NO: " + noVoters.size(), NamedTextColor.GRAY))
        );
    }

    public void endPoll() {
        if (!hasActivePoll()) return;
        if (expiryTask != null) { expiryTask.cancel(); expiryTask = null; }

        int yes = yesVoters.size();
        int no  = noVoters.size();
        String result;
        NamedTextColor resultColor;
        if (yes > no)       { result = "YES wins!";   resultColor = NamedTextColor.GREEN; }
        else if (no > yes)  { result = "NO wins!";    resultColor = NamedTextColor.RED; }
        else                { result = "It's a tie!"; resultColor = NamedTextColor.YELLOW; }

        Component bar = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);
        Bukkit.broadcast(bar);
        Bukkit.broadcast(
            Component.text("  POLL ENDED  ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(question, NamedTextColor.WHITE))
        );
        Bukkit.broadcast(
            Component.text("  YES: ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .append(Component.text(yes + "   ", NamedTextColor.WHITE))
                .append(Component.text("NO: ", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text(no + "   →  ", NamedTextColor.WHITE))
                .append(Component.text(result, resultColor).decorate(TextDecoration.BOLD))
        );
        Bukkit.broadcast(bar);

        question = null;
        yesVoters.clear();
        noVoters.clear();
    }

    private void cancelSilently() {
        if (expiryTask != null) { expiryTask.cancel(); expiryTask = null; }
        question = null;
        yesVoters.clear();
        noVoters.clear();
    }

    private void broadcastPollOpen() {
        Component yesBtn = Component.text("  ✔ YES  ", NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/poll yes"))
            .hoverEvent(HoverEvent.showText(Component.text("Click to vote YES", NamedTextColor.GREEN)));
        Component noBtn = Component.text("  ✗ NO  ", NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/poll no"))
            .hoverEvent(HoverEvent.showText(Component.text("Click to vote NO", NamedTextColor.RED)));
        Component bar = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);

        Bukkit.broadcast(bar);
        Bukkit.broadcast(
            Component.text("  POLL  ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(question, NamedTextColor.WHITE))
        );
        Bukkit.broadcast(
            Component.text("  [", NamedTextColor.DARK_GRAY)
                .append(yesBtn)
                .append(Component.text("]    [", NamedTextColor.DARK_GRAY))
                .append(noBtn)
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
        );
        Bukkit.broadcast(Component.text(
            "  Closes in " + DURATION_SECONDS + "s — click or type /poll yes|no",
            NamedTextColor.DARK_GRAY));
        Bukkit.broadcast(bar);
    }
}
