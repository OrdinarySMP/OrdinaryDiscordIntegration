package tronka.ordinarydiscordintegration.linking;

import com.google.common.collect.ImmutableList;
import net.minecraft.util.Uuids;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.Utils;

import java.util.*;

public class PlayerLink {
    private UUID playerId;
    private long discordId;
    private List<PlayerData> alts;
    private transient LinkData dataObj;

    public PlayerLink() {

    }

    public PlayerLink(LinkRequest request, long discordId) {
        this(request.getPlayerId(), discordId);
    }

    public PlayerLink(UUID playerId, long discordId) {
        this.playerId = playerId;
        this.discordId = discordId;
        alts = new ArrayList<>();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return Utils.getPlayerName(playerId);
    }

    public long getDiscordId() {
        return discordId;
    }

    public void addAlt(PlayerData data) {
        alts.add(data);
        dataObj.updatePlayerLink(this);
    }

    public void removeAlt(UUID uuid) {
        alts.removeIf(data -> data.getId().equals(uuid));
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

    @Override
    public String toString() {
        return "PlayerLink{" +
                "playerId=" + playerId +
                ", discordId=" + discordId +
                ", alts=" + alts +
                ", dataObj=" + dataObj +
                '}';
    }
}
