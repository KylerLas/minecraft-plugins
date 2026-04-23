package me.kaistudio;

import java.util.UUID;

public class Request {
    public final UUID id;
    public final UUID fromUUID;
    public final String fromName;
    public final UUID toUUID;
    public final String toName;
    public final int nuggets;

    public Request(UUID fromUUID, String fromName, UUID toUUID, String toName, int nuggets) {
        this.id = UUID.randomUUID();
        this.fromUUID = fromUUID;
        this.fromName = fromName;
        this.toUUID = toUUID;
        this.toName = toName;
        this.nuggets = nuggets;
    }
}
