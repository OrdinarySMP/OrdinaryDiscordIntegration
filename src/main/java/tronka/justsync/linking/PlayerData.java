package tronka.justsync.linking;

import java.util.UUID;
import tronka.justsync.Utils;

public class PlayerData {

    private UUID id;

    public PlayerData() {

    }

    public PlayerData(UUID id) {
        this.id = id;
    }

    public static PlayerData from(LinkRequest linkRequest) {
        return new PlayerData(linkRequest.getPlayerId());
    }

    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return Utils.getPlayerName(this.id);
    }
}
