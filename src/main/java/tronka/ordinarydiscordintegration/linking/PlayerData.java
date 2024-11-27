package tronka.ordinarydiscordintegration.linking;

import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;

import java.util.UUID;

public class PlayerData {
    private UUID id;

    public PlayerData() {

    }

    public PlayerData(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        var player = OrdinaryDiscordIntegration.getInstance().getServer().getPlayerManager().getPlayer(id);
        if (player != null) {
            return player.getName().getLiteralString();
        }
        return "";
    }

    public static PlayerData from(LinkRequest linkRequest) {
        return new PlayerData(linkRequest.getPlayerId());
    }
}
