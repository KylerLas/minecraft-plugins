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

    public DatabaseManager(String connectionString, String database, String finesCollection) {
        client = MongoClients.create(connectionString);
        MongoDatabase db = client.getDatabase(database);
        fines = db.getCollection(finesCollection);
        players = db.getCollection("minecraft_players");
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

    public void updatePlayerGold(String playerUuid, int nuggets) {
        players.updateOne(
            Filters.eq("playerUuid", playerUuid),
            Updates.set("gold", nuggets)
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

    public void close() {
        if (client != null) client.close();
    }
}
