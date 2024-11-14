package tronka.ordinarydiscordintegration.linking;

import java.util.UUID;

public class LinkRequest {
    private UUID playerId;
    private String name;
    private long expiresAt;

    public LinkRequest(UUID playerId, String name, long expiresAt) {
        this.playerId = playerId;
        this.name = name;
        this.expiresAt = expiresAt;
    }

    public UUID getPlayerId() {
        return playerId;
    }
    public String getName() {
        return name;
    }
    public boolean isExpired() {
        return expiresAt < System.currentTimeMillis();
    }
}
