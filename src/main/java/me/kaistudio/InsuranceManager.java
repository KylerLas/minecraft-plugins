package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class InsuranceManager {

    // 1-minute check interval — charges each player when their individual timer expires
    private static final long CHECK_INTERVAL_TICKS = 1200L;
    static final long BILLING_INTERVAL_MS = 24000L * 50L; // 20 real-world minutes

    private static final List<String> TIERS = List.of("bronze", "silver", "gold");

    private final AnnouncePlugin plugin;

    // Server-wide config (loaded from DB, defaults used until loaded)
    private double baseDeathPenalty = 0.50;
    private boolean insuranceEnabled = true;
    private final Map<String, Double> deathRates = new HashMap<>();
    private final Map<String, Double> dailyCostRates = new HashMap<>();

    // Per-player in-memory state, populated on join
    private final Map<UUID, PlayerInsuranceState> playerStates = new HashMap<>();

    // Pending subscribe/change confirmations waiting for [Accept] click
    private final Map<UUID, String> pendingConfirm = new HashMap<>();

    public InsuranceManager(AnnouncePlugin plugin) {
        this.plugin = plugin;
        applyDefaults();
        loadConfigAsync();
        startBillingCheck();
    }

    private void applyDefaults() {
        deathRates.put("bronze", 0.35);
        deathRates.put("silver", 0.20);
        deathRates.put("gold", 0.08);
        dailyCostRates.put("bronze", 0.005);
        dailyCostRates.put("silver", 0.010);
        dailyCostRates.put("gold", 0.020);
    }

    private void loadConfigAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Document cfg = plugin.getDatabaseManager().getInsuranceConfig();
            if (cfg != null) Bukkit.getScheduler().runTask(plugin, () -> applyConfig(cfg));
        });
    }

    private void applyConfig(Document cfg) {
        if (cfg.containsKey("baseDeathPenalty")) baseDeathPenalty = cfg.getDouble("baseDeathPenalty");
        if (cfg.containsKey("insuranceEnabled")) insuranceEnabled = cfg.getBoolean("insuranceEnabled");
        for (String tier : TIERS) {
            Document td = cfg.get(tier, Document.class);
            if (td == null) continue;
            if (td.containsKey("deathRate")) deathRates.put(tier, td.getDouble("deathRate"));
            if (td.containsKey("dailyCostRate")) dailyCostRates.put(tier, td.getDouble("dailyCostRate"));
        }
    }

    private void startBillingCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::runBillingCheck, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    private void runBillingCheck() {
        if (!insuranceEnabled) return;
        Date now = new Date();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInsuranceState state = playerStates.get(player.getUniqueId());
            if (state == null || state.tier == null) continue;
            if (state.nextPaymentTime != null && !state.nextPaymentTime.after(now)) {
                chargePlayer(player, state);
                applyPendingTierChange(player, state);
            }
        }
    }

    private void chargePlayer(Player player, PlayerInsuranceState state) {
        double rate = dailyCostRates.getOrDefault(state.tier, 0.0);
        int totalNuggets = plugin.getDeathStateManager().getTotalNuggets(player);
        int feeNuggets = (int) Math.floor(totalNuggets * rate);

        int actualCharge = 0;
        if (feeNuggets > 0) {
            int collected = plugin.getDeathStateManager().collectGold(player, feeNuggets);
            if (collected > feeNuggets) GoldUtil.addGold(player, collected - feeNuggets);
            actualCharge = Math.min(collected, feeNuggets);
        }

        state.nextPaymentTime = new Date(System.currentTimeMillis() + BILLING_INTERVAL_MS);

        player.sendMessage(Component.text(
            "India Insures You: " + cap(state.tier) + " Plan — Invoice: " + GoldUtil.format(actualCharge) + " collected. Thank you for your compliance.",
            NamedTextColor.GRAY));

        final String tier = state.tier;
        final Date now = new Date();
        final Date next = state.nextPaymentTime;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().updateInsuranceBilling(
                player.getUniqueId().toString(), tier, now, next));
    }

    private void applyPendingTierChange(Player player, PlayerInsuranceState state) {
        if (state.pendingTier == null) return;
        String oldTier = state.tier;

        if ("cancel".equals(state.pendingTier)) {
            state.tier = null;
            state.pendingTier = null;
            player.sendMessage(Component.text("India Insures You: Your plan has been terminated. We wish you the best. We mean that.", NamedTextColor.YELLOW));
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().clearPlayerInsurance(player.getUniqueId().toString()));
            return;
        }

        state.tier = state.pendingTier;
        state.pendingTier = null;

        // Charge immediately at new tier's rate for this cycle
        double rate = dailyCostRates.getOrDefault(state.tier, 0.0);
        int totalNuggets = plugin.getDeathStateManager().getTotalNuggets(player);
        int feeNuggets = (int) Math.floor(totalNuggets * rate);

        int actualCharge = 0;
        if (feeNuggets > 0) {
            int collected = plugin.getDeathStateManager().collectGold(player, feeNuggets);
            if (collected > feeNuggets) GoldUtil.addGold(player, collected - feeNuggets);
            actualCharge = Math.min(collected, feeNuggets);
        }

        player.sendMessage(Component.text(
            "India Insures You: Plan amended from " + cap(oldTier) + " to " + cap(state.tier) + ". First invoice collected: " + GoldUtil.format(actualCharge) + ".",
            NamedTextColor.GREEN));

        final String newTier = state.tier;
        final Date now = new Date();
        final Date next = state.nextPaymentTime;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().updateInsuranceBilling(
                player.getUniqueId().toString(), newTier, now, next));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void loadPlayerState(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Document doc = plugin.getDatabaseManager().getPlayerInsurance(player.getUniqueId().toString());
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerInsuranceState state = new PlayerInsuranceState();
                if (doc != null) {
                    state.tier = doc.getString("insuranceTier");
                    state.pendingTier = doc.getString("insurancePendingTier");
                    Object next = doc.get("insuranceNextPaymentTime");
                    if (next instanceof Date d) state.nextPaymentTime = d;
                }
                playerStates.put(player.getUniqueId(), state);

                // Charge immediately if overdue (missed while offline)
                if (state.tier != null && state.nextPaymentTime != null
                        && !state.nextPaymentTime.after(new Date())) {
                    chargePlayer(player, state);
                }
            });
        });
    }

    public void unloadPlayerState(UUID uuid) {
        playerStates.remove(uuid);
        pendingConfirm.remove(uuid);
    }

    // Called after [Accept] click for first subscription
    public void subscribe(Player player, String tier) {
        PlayerInsuranceState state = playerStates.computeIfAbsent(player.getUniqueId(), k -> new PlayerInsuranceState());

        int invNuggets = GoldUtil.countNuggets(player.getInventory());
        double rate = dailyCostRates.getOrDefault(tier, 0.0);
        int totalNuggets = plugin.getDeathStateManager().getTotalNuggets(player);
        int feeNuggets = (int) Math.floor(totalNuggets * rate);
        int actualCharge = Math.min(feeNuggets, invNuggets);
        if (actualCharge > 0) GoldUtil.removeGold(player, actualCharge);

        Date now = new Date();
        Date next = new Date(now.getTime() + BILLING_INTERVAL_MS);
        state.tier = tier;
        state.pendingTier = null;
        state.nextPaymentTime = next;

        player.sendMessage(Component.text(
            "India Insures You: " + cap(tier) + " Plan activated. " + GoldUtil.format(actualCharge) + " collected immediately. Next collection in 20 minutes. Welcome aboard.",
            NamedTextColor.GREEN));

        final Date signupTime = now;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().setPlayerInsuranceTier(
                player.getUniqueId().toString(), tier, null, signupTime, now, next));
    }

    // Called after [Accept] click for a tier change
    public void setPendingTier(Player player, String newTier) {
        PlayerInsuranceState state = playerStates.computeIfAbsent(player.getUniqueId(), k -> new PlayerInsuranceState());
        state.pendingTier = newTier;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().setInsurancePendingTier(player.getUniqueId().toString(), newTier));
    }

    public void setEnabled(boolean enabled) {
        this.insuranceEnabled = enabled;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().setInsuranceEnabled(enabled));
    }

    public void setBaseDeathPenalty(double rate) {
        this.baseDeathPenalty = rate;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().setBaseDeathPenalty(rate));
    }

    public void setTierRates(String tier, double deathRate, double dailyCostRate) {
        deathRates.put(tier, deathRate);
        dailyCostRates.put(tier, dailyCostRate);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            plugin.getDatabaseManager().setTierRates(tier, deathRate, dailyCostRate));
    }

    public void queuePendingConfirm(UUID uuid, String tier) { pendingConfirm.put(uuid, tier); }
    public String consumePendingConfirm(UUID uuid) { return pendingConfirm.remove(uuid); }
    public void clearPendingConfirm(UUID uuid) { pendingConfirm.remove(uuid); }

    public double getDeathTaxRate(UUID uuid) {
        PlayerInsuranceState state = playerStates.get(uuid);
        if (!insuranceEnabled || state == null || state.tier == null) return baseDeathPenalty;
        return deathRates.getOrDefault(state.tier, baseDeathPenalty);
    }

    public String getPlayerTier(UUID uuid) {
        PlayerInsuranceState state = playerStates.get(uuid);
        return state == null ? null : state.tier;
    }

    public String getPlayerPendingTier(UUID uuid) {
        PlayerInsuranceState state = playerStates.get(uuid);
        return state == null ? null : state.pendingTier;
    }

    public long getMinutesUntilNextPayment(UUID uuid) {
        PlayerInsuranceState state = playerStates.get(uuid);
        if (state == null || state.nextPaymentTime == null) return 20;
        long diff = state.nextPaymentTime.getTime() - System.currentTimeMillis();
        return Math.max(0, diff / 60000);
    }

    public boolean isEnabled() { return insuranceEnabled; }
    public boolean isValidTier(String tier) { return TIERS.contains(tier); }
    public double getDeathRate(String tier) { return deathRates.getOrDefault(tier, 0.35); }
    public double getDailyCostRate(String tier) { return dailyCostRates.getOrDefault(tier, 0.005); }
    public double getBaseDeathPenalty() { return baseDeathPenalty; }

    public boolean isTierHigher(String newTier, String current) {
        return TIERS.indexOf(newTier) > TIERS.indexOf(current);
    }

    static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static class PlayerInsuranceState {
        String tier;
        String pendingTier;
        Date nextPaymentTime;
    }
}
