package tronka.ordinarydiscordintegration.linking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class JsonLinkData implements LinkData {
    private List<PlayerLink> links;
    private final File file;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    private JsonLinkData(File file) {
        this.file = file;
        if (!file.exists()) {
            links = new ArrayList<>();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            links = gson.fromJson(reader, new TypeToken<ArrayList<PlayerLink>>() { });
        } catch (Exception e) {
            throw new RuntimeException("Cannot load player links (%s)".formatted(file.getAbsolutePath()), e);
        }
        if (links == null) {
            // gson failed to parse a valid list
            links = new ArrayList<>();
        }
        links.forEach(link -> link.setDataObj(this));
        System.out.println("Loaded " + links.size() + " player links");
    }

    @Override
    public Optional<PlayerLink> getPlayerLink(UUID playerId) {
        return links.stream().filter(link -> playerId.equals(link.getPlayerId()) || link.hasAlt(playerId)).findFirst();
    }

    @Override
    public Optional<PlayerLink> getPlayerLink(long discordId) {
        return links.stream().filter(link -> discordId == link.getDiscordId()).findFirst();
    }

    @Override
    public void addPlayerLink(PlayerLink playerLink) {
        links.add(playerLink);
        playerLink.setDataObj(this);
        onUpdated();
    }

    @Override
    public void removePlayerLink(PlayerLink playerLink) {
        links.remove(playerLink);
        onUpdated();
    }

    @Override
    public void updatePlayerLink(PlayerLink playerLink) {
        onUpdated();
    }

    private void onUpdated() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(links, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save link data to file {}", file.getAbsolutePath(), e);
        }
    }

    public static LinkData from(File file) {
        return new JsonLinkData(file);
    }

    @Override
    public Stream<PlayerLink> getPlayerLinks() {
        return links.stream();
    }
}
