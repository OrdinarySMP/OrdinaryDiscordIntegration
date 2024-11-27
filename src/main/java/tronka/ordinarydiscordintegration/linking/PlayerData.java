package tronka.ordinarydiscordintegration.linking;

import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.Utils;

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
        return Utils.getPlayerName(id);
    }

    public static PlayerData from(LinkRequest linkRequest) {
        return new PlayerData(linkRequest.getPlayerId());
    }
}
