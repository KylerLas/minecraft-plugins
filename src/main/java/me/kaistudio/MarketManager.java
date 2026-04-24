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

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MarketManager {

    static final NamespacedKey TELLER_TAG = new NamespacedKey("myfirstplugin", "bank_teller");

    private static final double FLOOR             = 0.30;
    private static final double DECAY_PER_STACK   = 0.03;
    private static final double RECOVERY_PER_STEP = 0.01; // per 10-minute tick
    private static final int    SELLER_THRESHOLD  = 2;    // decay only when uniqueSellers > 2
    private static final long   WINDOW_MS         = 3_600_000L; // 1-hour rolling window

    private final AnnouncePlugin plugin;
    private final File pricesFile;
    private final File stateFile;

    // nuggets per single item at base (100%) price
    private final Map<Material, Integer> basePrices    = new EnumMap<>(Material.class);
    // current market multiplier per item (1.0 = full, 0.30 = floor)
    private final Map<Material, Double>  multipliers   = new EnumMap<>(Material.class);
    // rolling seller window: material -> uuid -> timestamp of their most recent sale
    private final Map<Material, Map<UUID, Long>> recentSellers = new EnumMap<>(Material.class);

    private final Set<UUID> tellerEntityUuids = new HashSet<>();

    public MarketManager(AnnouncePlugin plugin) {
        this.plugin = plugin;
        pricesFile = new File(plugin.getDataFolder(), "market_prices.yml");
        stateFile  = new File(plugin.getDataFolder(), "market_state.yml");
        initPricesFile();
        loadPrices();
        loadState();
        scanForTellers();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private void initPricesFile() {
        if (pricesFile.exists()) return;
        try {
            pricesFile.getParentFile().mkdirs();
            pricesFile.createNewFile();
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.set("prices.IRON_INGOT", 2);
            defaults.save(pricesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPrices() {
        basePrices.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(pricesFile);
        if (!cfg.isConfigurationSection("prices")) return;
        for (String key : cfg.getConfigurationSection("prices").getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                plugin.getLogger().warning("[Market] Unknown material in market_prices.yml: " + key);
                continue;
            }
            basePrices.put(mat, cfg.getInt("prices." + key));
        }
        plugin.getLogger().info("[Market] Loaded " + basePrices.size() + " tradeable item(s).");
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

    public boolean isListed(Material mat) {
        return basePrices.containsKey(mat);
    }

    public int getBasePrice(Material mat) {
        return basePrices.getOrDefault(mat, 0);
    }

    public double getMultiplier(Material mat) {
        return multipliers.getOrDefault(mat, 1.0);
    }

    // Nuggets paid per single item at the current market rate
    public int getEffectivePricePerItem(Material mat) {
        int base = basePrices.getOrDefault(mat, 0);
        if (base == 0) return 0;
        return Math.max(1, (int)(base * getMultiplier(mat)));
    }

    // Sell the full stack in the player's main hand. Returns nuggets paid, or -1 if not listed.
    public int processSale(Player player, ItemStack stack) {
        Material mat = stack.getType();
        if (!isListed(mat)) return -1;

        int amount = stack.getAmount();
        int payout = amount * getEffectivePricePerItem(mat);

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        GoldUtil.addGold(player, payout);

        applyDecay(mat, player.getUniqueId(), amount);
        return payout;
    }

    private void applyDecay(Material mat, UUID sellerUuid, int amount) {
        Map<UUID, Long> sellers = recentSellers.computeIfAbsent(mat, k -> new HashMap<>());
        sellers.put(sellerUuid, System.currentTimeMillis());

        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        long uniqueCount = sellers.values().stream().filter(ts -> ts >= cutoff).count();

        if (uniqueCount <= SELLER_THRESHOLD) return;

        double stacks  = Math.ceil(amount / 64.0);
        double current = multipliers.getOrDefault(mat, 1.0);
        double next    = Math.max(FLOOR, current - stacks * DECAY_PER_STACK);
        multipliers.put(mat, next);
        saveState();
    }

    // Called every 10 minutes — nudges depressed prices back toward 100%
    public void recoverPrices() {
        boolean changed = false;
        for (Material mat : basePrices.keySet()) {
            double current = multipliers.getOrDefault(mat, 1.0);
            if (current < 1.0) {
                multipliers.put(mat, Math.min(1.0, current + RECOVERY_PER_STEP));
                changed = true;
            }
        }
        if (changed) saveState();
    }

    public void spawnTeller(Player player) {
        Villager teller = (Villager) player.getWorld().spawnEntity(player.getLocation(), EntityType.VILLAGER);
        teller.setAI(false);
        teller.setInvulnerable(true);
        teller.setCustomNameVisible(true);
        teller.customName(Component.text("Bank Teller", NamedTextColor.GOLD));
        teller.setProfession(Villager.Profession.ARMORER);
        teller.setVillagerType(Villager.Type.PLAINS);
        teller.getPersistentDataContainer().set(TELLER_TAG, PersistentDataType.BYTE, (byte) 1);
        tellerEntityUuids.add(teller.getUniqueId());
    }

    // Shared helper used by PriceCommand and MarketTellerListener
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
        int blocks  = nuggets / 81;
        int rem     = nuggets % 81;
        int ingots  = rem / 9;
        int nug     = rem % 9;
        StringBuilder sb = new StringBuilder();
        if (blocks > 0) sb.append(blocks).append(" gold block").append(blocks > 1 ? "s" : "");
        if (ingots > 0) { if (sb.length() > 0) sb.append(", "); sb.append(ingots).append(" gold ingot").append(ingots > 1 ? "s" : ""); }
        if (nug    > 0) { if (sb.length() > 0) sb.append(", "); sb.append(nug).append(" gold nugget").append(nug > 1 ? "s" : ""); }
        return sb.length() > 0 ? sb.toString() : "0 gold nuggets";
    }
}
