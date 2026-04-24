package me.kaistudio;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChestTracker {

    private final File file;
    private YamlConfiguration config;

    // "world:x:y:z" -> playerUUID string
    private final Map<String, String> chests = new HashMap<>();
    private final Map<String, String> goldBlocks = new HashMap<>();

    public ChestTracker(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "chest_tracker.yml");
        load();
    }

    private void load() {
        config = YamlConfiguration.loadConfiguration(file);
        chests.clear();
        goldBlocks.clear();
        if (config.isConfigurationSection("chests")) {
            for (String key : config.getConfigurationSection("chests").getKeys(false)) {
                chests.put(key, config.getString("chests." + key));
            }
        }
        if (config.isConfigurationSection("gold_blocks")) {
            for (String key : config.getConfigurationSection("gold_blocks").getKeys(false)) {
                goldBlocks.put(key, config.getString("gold_blocks." + key));
            }
        }
    }

    private void save() {
        config.set("chests", null);
        config.set("gold_blocks", null);
        for (Map.Entry<String, String> e : chests.entrySet())
            config.set("chests." + e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : goldBlocks.entrySet())
            config.set("gold_blocks." + e.getKey(), e.getValue());
        try {
            config.save(file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private Optional<Location> parseKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return Optional.empty();
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return Optional.empty();
        try {
            return Optional.of(new Location(world,
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public void trackChest(Location loc, UUID playerUUID) {
        chests.put(key(loc), playerUUID.toString());
        save();
    }

    public void untrackChest(Location loc) {
        if (chests.remove(key(loc)) != null) save();
    }

    public void trackGoldBlock(Location loc, UUID playerUUID) {
        goldBlocks.put(key(loc), playerUUID.toString());
        save();
    }

    public void untrackGoldBlock(Location loc) {
        if (goldBlocks.remove(key(loc)) != null) save();
    }

    public List<Location> getChestLocations(UUID playerUUID) {
        List<Location> locs = new ArrayList<>();
        for (Map.Entry<String, String> e : chests.entrySet()) {
            if (e.getValue().equals(playerUUID.toString())) {
                parseKey(e.getKey()).ifPresent(locs::add);
            }
        }
        return locs;
    }

    public int getGoldBlockCount(UUID playerUUID) {
        int count = 0;
        for (String uuid : goldBlocks.values()) {
            if (uuid.equals(playerUUID.toString())) count++;
        }
        return count;
    }

    public List<Location> getGoldBlockLocations(UUID playerUUID) {
        List<Location> locs = new ArrayList<>();
        for (Map.Entry<String, String> e : goldBlocks.entrySet()) {
            if (e.getValue().equals(playerUUID.toString())) {
                parseKey(e.getKey()).ifPresent(locs::add);
            }
        }
        return locs;
    }

    public Optional<UUID> getChestOwner(Location loc) {
        String uuid = chests.get(key(loc));
        return uuid == null ? Optional.empty() : Optional.of(UUID.fromString(uuid));
    }

    public Optional<UUID> getGoldBlockOwner(Location loc) {
        String uuid = goldBlocks.get(key(loc));
        return uuid == null ? Optional.empty() : Optional.of(UUID.fromString(uuid));
    }
}
