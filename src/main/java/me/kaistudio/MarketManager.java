package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MarketManager {

    static final NamespacedKey TELLER_TAG = new NamespacedKey("myfirstplugin", "bank_teller");

    private static final double FLOOR             = 0.30;
    private static final double DECAY_PER_STACK   = 0.05; // -5% per stack sold
    private static final double RECOVERY_PER_STEP = 0.02; // +2% every 3 minutes
    private static final long   WINDOW_MS         = 3_600_000L;

    // Canonical price bundle: sell qty items → receive nuggets gold
    record PriceEntry(int qty, int nuggets) {
        double perItem() { return (double) nuggets / qty; }
    }

    private final AnnouncePlugin plugin;
    private final File stateFile;

    private final Map<Material, PriceEntry> prices      = new EnumMap<>(Material.class);
    private final Map<Material, Double>     multipliers = new EnumMap<>(Material.class);
    private final Map<Material, Map<UUID, Long>> recentSellers = new EnumMap<>(Material.class);
    private final Set<UUID> tellerEntityUuids = new HashSet<>();

    private double depercentageMultiplier = 1.0;
    private double recoveryStep = RECOVERY_PER_STEP;
    private long recoveryIntervalTicks = 3600L;
    private BukkitTask recoveryTask = null;

    private boolean purgeActive = false;
    private final Map<Material, Double> purgeSnapshot = new EnumMap<>(Material.class);

    public MarketManager(AnnouncePlugin plugin) {
        this.plugin = plugin;
        stateFile = new File(plugin.getDataFolder(), "market_state.yml");
        plugin.saveResource("market_prices.yml", true); // always overwrite from JAR so format stays current
        loadPrices();
        loadState();
        scanForTellers();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void loadPrices() {
        prices.clear();
        File pricesFile = new File(plugin.getDataFolder(), "market_prices.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(pricesFile);
        if (!cfg.isConfigurationSection("prices")) {
            plugin.getLogger().severe("[Market] market_prices.yml failed to parse or has no 'prices' section — check for duplicate keys!");
            return;
        }
        for (String key : cfg.getConfigurationSection("prices").getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                plugin.getLogger().warning("[Market] Unknown material in market_prices.yml: " + key);
                continue;
            }
            String val = cfg.getString("prices." + key, "");
            String[] parts = val.split(":");
            if (parts.length != 2) {
                plugin.getLogger().warning("[Market] Bad format for " + key + " (expected qty:nuggets): " + val);
                continue;
            }
            try {
                int qty     = Integer.parseInt(parts[0].trim());
                int nuggets = Integer.parseInt(parts[1].trim());
                if (qty > 0 && nuggets > 0) prices.put(mat, new PriceEntry(qty, nuggets));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[Market] Bad numbers for " + key + ": " + val);
            }
        }
        plugin.getLogger().info("[Market] Loaded " + prices.size() + " tradeable item(s).");
    }

    private void loadState() {
        multipliers.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(stateFile);
        if (!cfg.isConfigurationSection("multipliers")) return;
        for (String key : cfg.getConfigurationSection("multipliers").getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat != null) multipliers.put(mat, cfg.getDouble("multipliers." + key));
        }
    }

    private void saveState() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<Material, Double> e : multipliers.entrySet()) {
            cfg.set("multipliers." + e.getKey().name(), e.getValue());
        }
        try { cfg.save(stateFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void scanForTellers() {
        tellerEntityUuids.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(TELLER_TAG, PersistentDataType.BYTE)) {
                    tellerEntityUuids.add(entity.getUniqueId());
                }
            }
        }
        plugin.getLogger().info("[Market] Found " + tellerEntityUuids.size() + " bank teller(s).");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isTeller(Entity entity) {
        return tellerEntityUuids.contains(entity.getUniqueId());
    }

    public int getPriceCount() { return prices.size(); }

    public boolean isListed(Material mat) {
        return prices.containsKey(mat);
    }

    public double getMultiplier(Material mat) {
        if (purgeActive) return 2.0;
        return multipliers.getOrDefault(mat, 1.0);
    }

    public PriceEntry getBaseEntry(Material mat) {
        return prices.get(mat);
    }

    // Nuggets paid for selling `amount` items at current market rate
    public int calculatePayout(Material mat, int amount) {
        PriceEntry entry = prices.get(mat);
        if (entry == null) return 0;
        return (int)(amount * entry.perItem() * getMultiplier(mat));
    }

    // Sell the full stack in the player's main hand. Returns nuggets paid, or -1 if not listed.
    public int processSale(Player player, ItemStack stack) {
        Material mat = stack.getType();
        if (!isListed(mat)) return -1;

        int amount = stack.getAmount();
        int payout = calculatePayout(mat, amount);

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        GoldUtil.addGold(player, payout);
        applyDecay(mat, player.getUniqueId(), amount);
        return payout;
    }

    private void applyDecay(Material mat, UUID sellerUuid, int amount) {
        if (purgeActive) return;
        recentSellers.computeIfAbsent(mat, k -> new HashMap<>())
                     .put(sellerUuid, System.currentTimeMillis());

        double stacks  = amount / 64.0;
        double current = multipliers.getOrDefault(mat, 1.0);
        multipliers.put(mat, Math.max(FLOOR, current - stacks * DECAY_PER_STACK * depercentageMultiplier));
        saveState();
    }

    public void resetMarket() {
        multipliers.clear();
        recentSellers.clear();
        saveState();
    }

    // Returns up to n items sorted by most-depressed multiplier (lowest first)
    public List<Map.Entry<Material, Double>> getTopDepressedItems(int n) {
        return multipliers.entrySet().stream()
            .filter(e -> prices.containsKey(e.getKey()) && e.getValue() < 1.0)
            .sorted(Map.Entry.comparingByValue())
            .limit(n)
            .collect(java.util.stream.Collectors.toList());
    }

    public void recoverPrices() {
        if (purgeActive) return;
        boolean changed = false;
        for (Material mat : prices.keySet()) {
            double current = multipliers.getOrDefault(mat, 1.0);
            if (current < 1.0) {
                multipliers.put(mat, Math.min(1.0, current + recoveryStep));
                changed = true;
            }
        }
        if (changed) saveState();
    }

    public void spawnTeller(Player player) {
        Villager teller = (Villager) player.getWorld().spawnEntity(player.getLocation(), EntityType.VILLAGER);
        teller.setAI(false);
        teller.setInvulnerable(true);
        teller.setSilent(true);
        teller.setCustomNameVisible(true);
        teller.customName(Component.text("Bank Teller", NamedTextColor.GOLD));
        teller.setProfession(Villager.Profession.ARMORER);
        teller.setVillagerType(Villager.Type.PLAINS);
        teller.setRecipes(java.util.Collections.emptyList());
        teller.getPersistentDataContainer().set(TELLER_TAG, PersistentDataType.BYTE, (byte) 1);
        tellerEntityUuids.add(teller.getUniqueId());
    }

    public void enterPurge() {
        purgeSnapshot.clear();
        purgeSnapshot.putAll(multipliers);
        purgeActive = true;
    }

    public void exitPurge() {
        purgeActive = false;
        multipliers.clear();
        multipliers.putAll(purgeSnapshot);
        purgeSnapshot.clear();
        saveState();
    }

    public void startRecoveryTask() {
        if (recoveryTask != null) recoveryTask.cancel();
        recoveryTask = Bukkit.getScheduler().runTaskTimer(
            plugin, this::recoverPrices, recoveryIntervalTicks, recoveryIntervalTicks);
    }

    public void setRecoveryParams(int minutes, int pct) {
        recoveryIntervalTicks = (long) minutes * 60 * 20;
        recoveryStep = pct / 100.0;
        startRecoveryTask();
    }

    public void setDepercentageMultiplier(int amount) {
        depercentageMultiplier = amount / 100.0;
    }

    // ── Shared display helpers ────────────────────────────────────────────────

    public static String formatMaterial(Material mat) {
        String[] words = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static String formatNuggets(int nuggets) {
        int blocks = nuggets / 81;
        int rem    = nuggets % 81;
        int ingots = rem / 9;
        int nug    = rem % 9;
        StringBuilder sb = new StringBuilder();
        if (blocks > 0) sb.append(blocks).append(" gold block").append(blocks > 1 ? "s" : "");
        if (ingots > 0) { if (sb.length() > 0) sb.append(", "); sb.append(ingots).append(" gold ingot").append(ingots > 1 ? "s" : ""); }
        if (nug    > 0) { if (sb.length() > 0) sb.append(", "); sb.append(nug).append(" gold nugget").append(nug > 1 ? "s" : ""); }
        return sb.length() > 0 ? sb.toString() : "0 gold nuggets";
    }
}
