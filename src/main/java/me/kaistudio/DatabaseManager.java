package me.kaistudio;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.Date;

public class DatabaseManager {

    private MongoClient client;
    private MongoCollection<Document> fines;

    public DatabaseManager(String connectionString, String database, String collection) {
        client = MongoClients.create(connectionString);
        MongoDatabase db = client.getDatabase(database);
        fines = db.getCollection(collection);
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

    public void close() {
        if (client != null) client.close();
    }
}
