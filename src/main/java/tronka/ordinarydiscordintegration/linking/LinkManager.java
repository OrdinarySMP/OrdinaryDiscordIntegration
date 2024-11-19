package tronka.ordinarydiscordintegration.linking;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.entities.Member;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.*;
import java.util.stream.Collectors;

public class LinkManager {

//    static BiMap<UUID, Long> playerLinks = HashBiMap.create();
//    static Map<UUID, UUID> altMap = new HashMap<>();
//
    static Map<String, LinkRequest> linkRequests = new HashMap<>();
    private static final int PURGE_LIMIT = 30;
    private static final Random RANDOM = new Random();
    static LinkData linkData = JsonLinkData.from(FabricLoader.getInstance().getConfigDir().resolve(OrdinaryDiscordIntegration.ModId + ".player-links.json").toFile());


    public static Optional<Member> getDiscordOf(UUID playerId) {
        var link = linkData.getPlayerLink(playerId);
        if (link.isPresent()) {
            var member = OrdinaryDiscordIntegration.guild.getMemberById(link.get().getDiscordId());
            if (member != null) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    public static Optional<PlayerLink> getDataOf(long discordId) {
        return linkData.getPlayerLink(discordId);
    }

    public static Optional<PlayerLink> getDataOf(UUID playerId) {
        return linkData.getPlayerLink(playerId);
    }


    public static boolean canJoin(UUID playerId) {
        if (!Config.INSTANCE.joining.enableLinking) {return true;}
        var member = getDiscordOf(playerId);
        return member.filter(
                value -> Set.copyOf(value.getRoles()).containsAll(OrdinaryDiscordIntegration.requiredRolesToJoin))
                .isPresent();
    }

    public static String getJoinError(GameProfile profile) {
        var member = getDiscordOf(profile.getId());
        if (member.isEmpty()) {
            var code = generateLinkCode(profile);
            return Config.INSTANCE.strings.kickLinkCode.formatted(code);
        }
        return Config.INSTANCE.strings.kickMissingRoles;
    }

    public static String confirmLink(long discordId, String code) {
        if (linkData.getPlayerLink(discordId).isPresent()) { return Config.INSTANCE.linkResults.failedTooManyLinked; }
        var linkRequest = getPlayerLinkFromCode(code);
        if (linkRequest.isEmpty()) { return Config.INSTANCE.linkResults.failedUnknownCode; }
        linkData.addPlayerLink(new PlayerLink(linkRequest.get(), discordId));
        return Config.INSTANCE.linkResults.linkSuccess
                .replace("%name%", linkRequest.get().getName());
    }

    private static Optional<LinkRequest> getPlayerLinkFromCode(String code) {
        if (!linkRequests.containsKey(code)) {
            return Optional.empty();
        }
        var request = linkRequests.remove(code);
        if (request.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public static String generateLinkCode(GameProfile profile) {
        if (linkRequests.size() >= PURGE_LIMIT) {
            purgeCodes();
        }

        long expiryTime = System.currentTimeMillis() + Config.INSTANCE.joining.linkCodeExpireMinutes * 60 * 1000;
        String code;
        do {
            code = String.valueOf(RANDOM.nextInt(100000, 1000000));  // 6 digit code
        }
        while (linkRequests.containsKey(code));
        linkRequests.put(code, new LinkRequest(profile.getId(), profile.getName(), expiryTime));
        return code;
    }

    private static void purgeCodes() {
        long time = System.currentTimeMillis();

        linkRequests.entrySet().removeIf(request -> request.getValue().isExpired());
    }

    public static void unlinkPlayers(List<Member> members) {
        var memberSet = members.stream().map(Member::getIdLong).collect(Collectors.toSet());
        final int[] purgedCount = {0};
        linkData.getPlayerLinks().forEach(link -> {
            if (!memberSet.contains(link.getDiscordId())) {
                linkData.removePlayerLink(link);
                OrdinaryDiscordIntegration.LOGGER.info("Removed link {} from members list", link.getDiscordId());
                purgedCount[0] += 1;
            }
        });
        if (purgedCount[0] != 0) {
            OrdinaryDiscordIntegration.LOGGER.info("Purged {} linked players", purgedCount[0]);
        }
    }

    public static void unlinkPlayer(long id) {
        linkData.getPlayerLink(id).ifPresent(linkData::removePlayerLink);
    }

}
