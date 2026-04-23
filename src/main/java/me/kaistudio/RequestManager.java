package me.kaistudio;

import java.util.*;
import java.util.stream.Collectors;

public class RequestManager {

    private final Map<UUID, Request> requests = new HashMap<>();

    public Request create(UUID fromUUID, String fromName, UUID toUUID, String toName, int nuggets) {
        Request req = new Request(fromUUID, fromName, toUUID, toName, nuggets);
        requests.put(req.id, req);
        return req;
    }

    public Optional<Request> get(UUID requestId) {
        return Optional.ofNullable(requests.get(requestId));
    }

    public void remove(UUID requestId) {
        requests.remove(requestId);
    }

    public List<Request> getSent(UUID playerUUID) {
        return requests.values().stream()
            .filter(r -> r.fromUUID.equals(playerUUID))
            .collect(Collectors.toList());
    }

    public List<Request> getReceived(UUID playerUUID) {
        return requests.values().stream()
            .filter(r -> r.toUUID.equals(playerUUID))
            .collect(Collectors.toList());
    }
}
