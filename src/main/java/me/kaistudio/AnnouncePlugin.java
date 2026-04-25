package me.kaistudio;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Random;

public class AnnouncePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private ChestTracker chestTracker;
    private RequestManager requestManager;
    private RequestCommand requestCommand;
    private DeathStateManager deathStateManager;
    private InsuranceManager insuranceManager;
    private MarketManager marketManager;

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ChestTracker getChestTracker() { return chestTracker; }
    public RequestManager getRequestManager() { return requestManager; }
    public RequestCommand getRequestCommand() { return requestCommand; }
    public DeathStateManager getDeathStateManager() { return deathStateManager; }
    public InsuranceManager getInsuranceManager() { return insuranceManager; }
    public MarketManager getMarketManager() { return marketManager; }

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
        deathStateManager = new DeathStateManager(this);
        insuranceManager = new InsuranceManager(this);
        marketManager = new MarketManager(this);

        Bukkit.getPluginManager().registerEvents(new ChickenDeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GoldDropListener(), this);
        Bukkit.getPluginManager().registerEvents(new GoldRestrictionListener(), this);
        Bukkit.getPluginManager().registerEvents(new DeathStateListener(this), this);
        Bukkit.getPluginManager().registerEvents(new InsuranceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MarketTellerListener(this), this);

        // Gold scanner — runs every 10 seconds (200 ticks)
        Bukkit.getScheduler().runTaskTimer(this, new GoldScanner(this), 200L, 200L);

        // Market price recovery — +2% every 3 minutes (3600 ticks), tuned for short sessions
        Bukkit.getScheduler().runTaskTimer(this, () -> marketManager.recoverPrices(), 3600L, 3600L);

        // Action bar — purgatory reminder for ghosts; chest ownership for everyone else
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (deathStateManager.isDead(player.getUniqueId())) {
                    player.sendActionBar(Component.text("☠ You are in Purgatory — type /pay death to revive", NamedTextColor.RED));
                    continue;
                }

                Block target = player.getTargetBlockExact(5);
                if (target == null) continue;
                Material type = target.getType();
                if (type != Material.CHEST && type != Material.TRAPPED_CHEST
                        && type != Material.BARREL && type != Material.ENDER_CHEST) continue;

                chestTracker.getChestOwner(target.getLocation()).ifPresent(ownerUuid -> {
                    String label = switch (type) {
                        case BARREL -> "Barrel";
                        case ENDER_CHEST -> "Ender Chest";
                        default -> "Chest";
                    };
                    if (ownerUuid.equals(player.getUniqueId())) {
                        player.sendActionBar(Component.text("Your " + label, NamedTextColor.GREEN));
                    } else {
                        String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                        player.sendActionBar(Component.text(
                            (ownerName != null ? ownerName : "Someone") + "'s " + label, NamedTextColor.RED));
                    }
                });
            }
        }, 20L, 20L);

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
            event.registrar().register(new GoldScoreCommand().build(), "View the gold leaderboard");
            event.registrar().register(new InsuranceCommand(this).build(), "Manage your Insurance Inc policy");
            event.registrar().register(new DeathPenaltyCommand(this).build(), "Set the base death penalty percentage (OP only)");
            event.registrar().register(new PriceCommand(this).build(), "Check the current bank price of the item in your hand");
            event.registrar().register(new BankCommand(this).build(), "Bank admin commands — reset, leaderboard");
            event.registrar().register(
                Commands.literal("spawnteller")
                    .executes(ctx -> {
                        if (!(ctx.getSource().getSender() instanceof Player player)) {
                            ctx.getSource().getSender().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                            return 0;
                        }
                        if (!player.isOp()) {
                            player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                            return 0;
                        }
                        marketManager.spawnTeller(player);
                        player.sendMessage(Component.text("Bank Teller spawned!", NamedTextColor.GREEN));
                        return 1;
                    })
                    .build(),
                "Spawn a Bank Teller NPC at your location (OP only)"
            );
        });

        getLogger().info("AnnouncePlugin enabled!");
    }
}
