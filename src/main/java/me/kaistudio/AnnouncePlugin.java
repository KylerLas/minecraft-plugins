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
    private ChestTracker chestTracker;
    private RequestManager requestManager;
    private RequestCommand requestCommand;

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ChestTracker getChestTracker() { return chestTracker; }
    public RequestManager getRequestManager() { return requestManager; }
    public RequestCommand getRequestCommand() { return requestCommand; }

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
        saveDefaultConfig();
        String connectionString = getConfig().getString("cosmos-connection-string");
        String database = getConfig().getString("cosmos-database");
        String collection = getConfig().getString("cosmos-collection");
        databaseManager = new DatabaseManager(connectionString, database, collection);
        getLogger().info("Connected to Cosmos DB!");

        chestTracker = new ChestTracker(this);
        requestManager = new RequestManager();
        requestCommand = new RequestCommand(this);

        Bukkit.getPluginManager().registerEvents(new ChickenDeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockListener(this), this);

        // Gold scanner — runs every 10 seconds (200 ticks)
        Bukkit.getScheduler().runTaskTimer(this, new GoldScanner(this), 200L, 200L);

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

            event.registrar().register(new PayCommand(this).build(), "Pay another player gold from your inventory");
            event.registrar().register(requestCommand.buildRequest(), "Request gold from another player");
            event.registrar().register(requestCommand.buildRequests(), "View and manage your gold requests");
        });

        getLogger().info("AnnouncePlugin enabled!");
    }
}
