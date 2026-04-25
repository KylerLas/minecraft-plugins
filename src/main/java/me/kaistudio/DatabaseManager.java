package me.kaistudio;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.Date;

public class DatabaseManager {

    private MongoClient client;
    private MongoCollection<Document> fines;
    private MongoCollection<Document> players;
    private MongoCollection<Document> config;

    public DatabaseManager(String connectionString, String database, String finesCollection) {
        client = MongoClients.create(connectionString);
        MongoDatabase db = client.getDatabase(database);
        fines = db.getCollection(finesCollection);
        players = db.getCollection("minecraft_players");
        config = db.getCollection("minecraft_config");
    }

    public void logFine(String playerName, String playerUuid, String reason, int amount) {
        Document fine = new Document()
            .append("playerName", playerName)
            .append("playerUuid", playerUuid)
            .append("reason", reason)
            .append("amount", amount)
            .append("paid", false)
            .append("collected", false)
            .append("timestamp", new Date());

        fines.insertOne(fine);
    }

    public void upsertPlayer(String playerName, String playerUuid) {
        players.updateOne(
            Filters.eq("playerUuid", playerUuid),
            Updates.combine(
                Updates.set("playerName", playerName),
                Updates.set("playerUuid", playerUuid),
                Updates.set("lastSeen", new Date()),
                Updates.setOnInsert("deaths", 0),
                Updates.setOnInsert("gold", 0),
                Updates.setOnInsert("transactionsSent", 0),
                Updates.setOnInsert("transactionsReceived", 0),
                Updates.setOnInsert("insuranceTier", null),
                Updates.setOnInsert("joinDate", new Date())
            ),
            new UpdateOptions().upsert(true)
        );
    }

    public void incrementPlayerDeaths(String playerName, String playerUuid) {
        players.updateOne(
            Filters.eq("playerUuid", playerUuid),
            Updates.combine(
                Updates.inc("deaths", 1),
                Updates.set("lastSeen", new Date())
            )
        );
    }

    public void updatePlayerGold(String playerUuid, double gold) {
        players.updateOne(
            Filters.eq("playerUuid", playerUuid),
            Updates.set("gold", gold)
        );
    }

    public void incrementTransactionsSent(String playerUuid) {
        players.updateOne(
            Filters.eq("playerUuid", playerUuid),
            Updates.inc("transactionsSent", 1)
        );
    }

    public void incrementTransactionsReceived(String playerUuid) {
        players.updateOne(
            Filters.eq("playerUuid", playerUuid),
            Updates.inc("transactionsReceived", 1)
        );
    }

    // ── Fines ─────────────────────────────────────────────────────────────────

    public void deleteAllFines() {
        fines.deleteMany(new org.bson.Document());
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    public void incrementMobKills(String playerUuid) {
        players.updateOne(Filters.eq("playerUuid", playerUuid), Updates.inc("mobKills", 1));
    }

    public void incrementPvpKillsDuringPurge(String playerUuid) {
        players.updateOne(Filters.eq("playerUuid", playerUuid), Updates.inc("pvpKillsDuringPurge", 1));
    }

    public void incrementBossesKilled(String playerUuid) {
        players.updateOne(Filters.eq("playerUuid", playerUuid), Updates.inc("bossesKilled", 1));
    }

    public void incrementGuardianKills(String playerUuid) {
        players.updateOne(Filters.eq("playerUuid", playerUuid), Updates.combine(
            Updates.inc("bossesKilled", 1),
            Updates.inc("guardianKills", 1)
        ));
    }

    public void incrementWitherKills(String playerUuid) {
        players.updateOne(Filters.eq("playerUuid", playerUuid), Updates.combine(
            Updates.inc("bossesKilled", 1),
            Updates.inc("witherKills", 1)
        ));
    }

    public void incrementDragonKills(String playerUuid) {
        players.updateOne(Filters.eq("playerUuid", playerUuid), Updates.combine(
            Updates.inc("bossesKilled", 1),
            Updates.inc("dragonKills", 1)
        ));
    }

    public void addGoldReceived(String playerUuid, int goldAmount) {
        players.updateOne(Filters.eq("playerUuid", playerUuid), Updates.inc("goldReceived", goldAmount));
    }

    public void addGoldSpent(String playerUuid, int goldAmount) {
        players.updateOne(Filters.eq("playerUuid", playerUuid), Updates.inc("goldSpent", goldAmount));
    }

    public void resetAllPlayerStats() {
        players.updateMany(new org.bson.Document(),
            Updates.combine(
                Updates.set("deaths", 0),
                Updates.set("mobKills", 0),
                Updates.set("pvpKillsDuringPurge", 0),
                Updates.set("goldReceived", 0),
                Updates.set("goldSpent", 0),
                Updates.set("bossesKilled", 0),
                Updates.set("guardianKills", 0),
                Updates.set("witherKills", 0),
                Updates.set("dragonKills", 0),
                Updates.set("transactionsSent", 0),
                Updates.set("transactionsReceived", 0)
            ));
    }

    // ── Insurance config ─────────────────────────────────────────────────────

    public Document getInsuranceConfig() {
        return config.find(Filters.eq("_id", "insurance_rates")).first();
    }

    public void setInsuranceEnabled(boolean enabled) {
        config.updateOne(Filters.eq("_id", "insurance_rates"),
            Updates.set("insuranceEnabled", enabled),
            new UpdateOptions().upsert(true));
    }

    public void setBaseDeathPenalty(double rate) {
        config.updateOne(Filters.eq("_id", "insurance_rates"),
            Updates.set("baseDeathPenalty", rate),
            new UpdateOptions().upsert(true));
    }

    public void setTierRates(String tier, double deathRate, double dailyCostRate) {
        config.updateOne(Filters.eq("_id", "insurance_rates"),
            Updates.combine(
                Updates.set(tier + ".deathRate", deathRate),
                Updates.set(tier + ".dailyCostRate", dailyCostRate)
            ),
            new UpdateOptions().upsert(true));
    }

    // ── Player insurance ──────────────────────────────────────────────────────

    public Document getPlayerInsurance(String playerUuid) {
        return players.find(Filters.eq("playerUuid", playerUuid)).first();
    }

    public void setPlayerInsuranceTier(String playerUuid, String tier, String pendingTier,
                                       java.util.Date signupTime, java.util.Date lastCharged, java.util.Date nextPayment) {
        players.updateOne(Filters.eq("playerUuid", playerUuid),
            Updates.combine(
                Updates.set("insuranceTier", tier),
                Updates.set("insurancePendingTier", pendingTier),
                Updates.set("insuranceSignupTime", signupTime),
                Updates.set("insuranceLastCharged", lastCharged),
                Updates.set("insuranceNextPaymentTime", nextPayment)
            ));
    }

    public void setInsurancePendingTier(String playerUuid, String pendingTier) {
        players.updateOne(Filters.eq("playerUuid", playerUuid),
            Updates.set("insurancePendingTier", pendingTier));
    }

    public void updateInsuranceBilling(String playerUuid, String tier,
                                       java.util.Date lastCharged, java.util.Date nextPayment) {
        players.updateOne(Filters.eq("playerUuid", playerUuid),
            Updates.combine(
                Updates.set("insuranceTier", tier),
                Updates.set("insurancePendingTier", null),
                Updates.set("insuranceLastCharged", lastCharged),
                Updates.set("insuranceNextPaymentTime", nextPayment)
            ));
    }

    public void clearPlayerInsurance(String playerUuid) {
        players.updateOne(Filters.eq("playerUuid", playerUuid),
            Updates.combine(
                Updates.set("insuranceTier", null),
                Updates.set("insurancePendingTier", null),
                Updates.set("insuranceLastCharged", null),
                Updates.set("insuranceNextPaymentTime", null)
            ));
    }

    public void close() {
        if (client != null) client.close();
    }
}
