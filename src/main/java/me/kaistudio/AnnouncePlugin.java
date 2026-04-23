package me.kaistudio;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Random;

public class AnnouncePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    private static final List<String> MESSAGES = List.of(
        "has a small penis!",
        "just tested a fart",
        "loves a granny with no teeth"
    );

    private final Random random = new Random();

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
    }

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                Commands.literal("announce")
                    .executes(ctx -> {
                        if (!ctx.getSource().getSender().hasPermission("kai.announce")) {
                            ctx.getSource().getSender().sendMessage(
                                Component.text("You don't have permission to use this command.", NamedTextColor.RED)
                            );
                            return 0;
                        }

                        String senderName = ctx.getSource().getSender().getName();
                        String message = MESSAGES.get(random.nextInt(MESSAGES.size()));

                        Title title = Title.title(
                            Component.text(senderName, NamedTextColor.GOLD),
                            Component.text(message, NamedTextColor.WHITE),
                            Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofSeconds(4),
                                Duration.ofMillis(500)
                            )
                        );

                        Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(title));
                        Bukkit.broadcast(
                            Component.text("[" + senderName + "] ", NamedTextColor.GOLD)
                                .append(Component.text(message, NamedTextColor.WHITE))
                        );
                        return 1;
                    })
                    .build(),
                "Broadcasts a random announcement to all players"
            );
        });
        saveDefaultConfig();
        String connectionString = getConfig().getString("cosmos-connection-string");
        String database = getConfig().getString("cosmos-database");
        String collection = getConfig().getString("cosmos-collection");
        databaseManager = new DatabaseManager(connectionString, database, collection);
        getLogger().info("Connected to Cosmos DB!");

        Bukkit.getPluginManager().registerEvents(new ChickenDeathListener(this), this);
        getLogger().info("AnnouncePlugin enabled!");
    }
}
