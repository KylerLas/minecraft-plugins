package me.kaistudio;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DeathStateManager {

    static final NamespacedKey SKULL_TAG = new NamespacedKey("myfirstplugin", "death_skull");

    private final AnnouncePlugin plugin;
    private final File file;
    private YamlConfiguration config;

    private final Set<UUID> deadPlayers = new HashSet<>();
    private final Set<UUID> pendingDeath = new HashSet<>();
    private final Map<UUID, ArmorStand> skulls = new HashMap<>();

    public DeathStateManager(AnnouncePlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "death_state.yml");
        cleanupOrphanSkulls();
        load();
    }

    // Remove any skull armor stands left over from a previous server run
    private void cleanupOrphanSkulls() {
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand as
                        && as.getPersistentDataContainer().has(SKULL_TAG, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    private void load() {
        config = YamlConfiguration.loadConfiguration(file);
        deadPlayers.clear();
        for (String s : config.getStringList("dead")) {
            try { deadPlayers.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        List<String> list = new ArrayList<>();
        for (UUID uuid : deadPlayers) list.add(uuid.toString());
        config.set("dead", list);
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public boolean isDead(UUID uuid) { return deadPlayers.contains(uuid); }

    public void markPendingDeath(UUID uuid) { pendingDeath.add(uuid); }

    // Consumes and returns the pending flag — used in PlayerRespawnEvent
    public boolean consumePendingDeath(UUID uuid) { return pendingDeath.remove(uuid); }

    // Clears without checking — used on disconnect before respawn
    public void clearPendingDeath(UUID uuid) { pendingDeath.remove(uuid); }

    public void enterDeathState(Player player) {
        deadPlayers.add(player.getUniqueId());
        save();
        spawnSkull(player);
    }

    public void exitDeathState(Player player) {
        deadPlayers.remove(player.getUniqueId());
        save();
        despawnSkull(player.getUniqueId());
    }

    public void spawnSkull(Player player) {
        despawnSkull(player.getUniqueId());
        Location loc = skullLocation(player.getLocation());
        ArmorStand stand = player.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setCustomNameVisible(false);
            as.getPersistentDataContainer().set(SKULL_TAG, PersistentDataType.BYTE, (byte) 1);

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(player);
            skull.setItemMeta(meta);
            as.getEquipment().setHelmet(skull);
        });
        skulls.put(player.getUniqueId(), stand);
    }

    public void updateSkullPosition(Player player) {
        ArmorStand stand = skulls.get(player.getUniqueId());
        if (stand == null || !stand.isValid()) {
            spawnSkull(player);
            return;
        }
        stand.teleport(skullLocation(player.getLocation()));
    }

    public void despawnSkull(UUID uuid) {
        ArmorStand stand = skulls.remove(uuid);
        if (stand != null && stand.isValid()) stand.remove();
    }

    private Location skullLocation(Location playerLoc) {
        return playerLoc.clone().add(0, 2.1, 0);
    }

    public double getDeathTaxRate(UUID playerUuid) {
        return plugin.getInsuranceManager().getDeathTaxRate(playerUuid);
    }

    public int getTotalNuggets(Player player) {
        int nuggets = GoldUtil.countNuggets(player.getInventory());
        ChestTracker tracker = plugin.getChestTracker();

        Set<Inventory> counted = new HashSet<>();
        for (Location loc : tracker.getChestLocations(player.getUniqueId())) {
            Block block = loc.getBlock();
            Inventory inv = null;
            if (block.getState() instanceof Chest chest) inv = chest.getInventory();
            else if (block.getState() instanceof Barrel barrel) inv = barrel.getInventory();
            if (inv == null || !counted.add(inv)) continue;
            nuggets += GoldUtil.countNuggets(inv);
        }

        nuggets += GoldUtil.countNuggets(player.getEnderChest());
        nuggets += tracker.getGoldBlockCount(player.getUniqueId()) * 81;

        return nuggets;
    }

    /**
     * Collect up to debtNuggets worth of gold from the player's sources.
     * Order: placed gold blocks (stop early) → chests/barrels → ender chest → inventory.
     * May overshoot if the last item taken is indivisible (e.g. ingot covers a nugget-sized gap).
     * Caller must refund any overage via GoldUtil.addGold().
     */
    public int collectGold(Player player, int debtNuggets) {
        if (debtNuggets <= 0) return 0;
        int collected = 0;
        ChestTracker tracker = plugin.getChestTracker();

        // Phase 1: placed gold blocks — break one at a time, stop once debt is covered
        for (Location loc : tracker.getGoldBlockLocations(player.getUniqueId())) {
            if (collected >= debtNuggets) break;
            Block block = loc.getBlock();
            if (block.getType() == Material.GOLD_BLOCK) {
                block.setType(Material.AIR);
                tracker.untrackGoldBlock(loc);
                collected += 81;
            }
        }

        // Phase 2: chests and barrels
        if (collected < debtNuggets) {
            Set<Inventory> counted = new HashSet<>();
            for (Location loc : tracker.getChestLocations(player.getUniqueId())) {
                if (collected >= debtNuggets) break;
                Block block = loc.getBlock();
                Inventory inv = null;
                if (block.getState() instanceof Chest chest) inv = chest.getInventory();
                else if (block.getState() instanceof Barrel barrel) inv = barrel.getInventory();
                if (inv == null || !counted.add(inv)) continue;
                collected += drainGoldFromInventory(inv, debtNuggets - collected);
            }
        }

        // Phase 3: ender chest
        if (collected < debtNuggets) {
            collected += drainGoldFromInventory(player.getEnderChest(), debtNuggets - collected);
        }

        // Phase 4: player inventory
        if (collected < debtNuggets) {
            collected += drainGoldFromInventory(player.getInventory(), debtNuggets - collected);
        }

        return collected;
    }

    // Remove up to maxNuggets worth of gold from an inventory, rounding up per item if needed
    private int drainGoldFromInventory(Inventory inv, int maxNuggets) {
        if (maxNuggets <= 0) return 0;
        int removed = 0;
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (removed >= maxNuggets) break;
            ItemStack item = contents[i];
            if (item == null) continue;
            int nuggetsPer = nuggetValue(item.getType());
            if (nuggetsPer == 0) continue;

            // Round up so a gap smaller than one item still takes that item (overage is refunded by caller)
            int take = (int) Math.ceil((double) (maxNuggets - removed) / nuggetsPer);
            take = Math.min(take, item.getAmount());

            removed += take * nuggetsPer;
            if (take >= item.getAmount()) {
                contents[i] = null;
            } else {
                contents[i].setAmount(item.getAmount() - take);
            }
        }
        inv.setStorageContents(contents);
        return removed;
    }

    private int nuggetValue(Material type) {
        return switch (type) {
            case GOLD_BLOCK -> 81;
            case GOLD_INGOT -> 9;
            case GOLD_NUGGET -> 1;
            default -> 0;
        };
    }
}
