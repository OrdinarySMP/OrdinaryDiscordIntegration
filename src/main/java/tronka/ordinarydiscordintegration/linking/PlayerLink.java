package tronka.ordinarydiscordintegration.linking;

import com.google.common.collect.ImmutableList;

import java.util.*;

public class PlayerLink {
    private UUID playerId;
    private String playerName;
    private long discordId;
    private List<PlayerData> alts;
    private transient LinkData dataObj;

    public PlayerLink() {

    }

    public PlayerLink(LinkRequest request, long discordId) {
        this(request.getPlayerId(), request.getName(), discordId);
    }

    public PlayerLink(UUID playerId, String playerName, long discordId) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.discordId = discordId;
        alts = new ArrayList<>();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String name) {
        if (playerName.equals(name)) { return; }
        playerName = name;
        dataObj.updatePlayerLink(this);
    }

    public long getDiscordId() {
        return discordId;
    }

    public void addAlt(PlayerData uuid) {
        alts.add(uuid);
        dataObj.updatePlayerLink(this);
    }

    public void removeAlt(UUID uuid) {
        alts.removeIf(player -> player.getId().equals(uuid));
        dataObj.updatePlayerLink(this);
    }

    public void removeAlt(PlayerData data) {
        alts.remove(data);
        dataObj.updatePlayerLink(this);
    }

    public boolean hasAlt(UUID uuid) {
        return alts.stream().map(PlayerData::getId).anyMatch(uuid::equals);
    }

    public int altCount() {
        return alts.size();
    }

    public ImmutableList<PlayerData> getAlts() {
        return ImmutableList.copyOf(alts);
    }

    public void setDataObj(LinkData dataObj) {
        this.dataObj = dataObj;
    }
}
