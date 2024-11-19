package tronka.ordinarydiscordintegration.linking;

import java.util.UUID;

public class PlayerData {
    private UUID id;
    private String name;
    public PlayerData(UUID id, String name) {}
    public UUID getId() {
        return id;
    }
    public String getName() {
        return name;
    }
}
