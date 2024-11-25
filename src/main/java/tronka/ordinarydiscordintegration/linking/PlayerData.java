package tronka.ordinarydiscordintegration.linking;

import java.util.UUID;

public record PlayerData(UUID id, String name) {
    public static PlayerData from(LinkRequest linkRequest) {
        return new PlayerData(linkRequest.getPlayerId(), linkRequest.getName());
    }
}
