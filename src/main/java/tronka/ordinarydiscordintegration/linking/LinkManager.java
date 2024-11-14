package tronka.ordinarydiscordintegration.linking;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.entities.Member;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.*;

public class LinkManager {

//    static BiMap<UUID, Long> playerLinks = HashBiMap.create();
//    static Map<UUID, UUID> altMap = new HashMap<>();
//
    static Map<String, LinkRequest> linkRequests = new HashMap<>();
    private static final int PURGE_LIMIT = 30;
    private static final Random RANDOM = new Random();
    static LinkData linkData = JsonLinkData.from(FabricLoader.getInstance().getConfigDir().resolve(OrdinaryDiscordIntegration.ModId + ".player-links.json").toFile());


    private static Optional<Member> getDiscordOf(UUID playerId) {
        var link = linkData.getPlayerLink(playerId);
        if (link.isPresent()) {
            var member = OrdinaryDiscordIntegration.guild.getMemberById(link.get().getDiscordId());
            if (member != null) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
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

    public static LinkResult confirmLink(long discordId, String code) {
        if (linkData.getPlayerLink(discordId).isPresent()) { return LinkResult.FAILED_ALREADY_LINKED; }
        var linkRequest = getPlayerLinkFromCode(code);
        if (linkRequest.isEmpty()) { return LinkResult.FAILED_UNKNOWN; }
        linkData.addPlayerLink(new PlayerLink(linkRequest.get(), discordId));
        return LinkResult.LINKED;
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


}
