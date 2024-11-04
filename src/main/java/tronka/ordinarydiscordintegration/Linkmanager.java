package tronka.ordinarydiscordintegration;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.dv8tion.jda.api.entities.Member;
import net.minecraft.util.Pair;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.*;

public class Linkmanager {

    static BiMap<UUID, Long> playerLinks = HashBiMap.create();

    static Map<String, Pair<UUID, Long>> linkCodes = new HashMap<>();
    private static final int PURGE_LIMIT = 120;
    private static final Random RANDOM = new Random();


    private static Optional<Member> getDiscordOf(UUID playerId) {
        if (playerLinks.containsKey(playerId)) {
            var dcId = playerLinks.get(playerId);
            var member = OrdinaryDiscordIntegration.guild.getMemberById(dcId);
            if (member != null) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }


    public static boolean canJoin(UUID playerId) {
        if (Config.INSTANCE.joining.enableLinking) {return true;}
        var member = getDiscordOf(playerId);
        return member.filter(
                value -> Set.copyOf(value.getRoles()).containsAll(OrdinaryDiscordIntegration.requiredRolesToJoin))
                .isPresent();
    }

    public static String getJoinError(UUID playerId) {
        var member = getDiscordOf(playerId);
        if (member.isEmpty()) {
            var code = generateLinkCode(playerId);
            return Config.INSTANCE. strings.kickLinkCode.formatted(code);
        }
        return Config.INSTANCE.strings.kickMissingRoles;
    }

    public static boolean confirmLink(long discordId, String code) {
        var playerId = getPlayerIdFromCode(code);
        if (playerId.isEmpty()) { return false; }
        playerLinks.put(playerId.get(), discordId);
        return true;
    }

    private static Optional<UUID> getPlayerIdFromCode(String code) {
        if (!linkCodes.containsKey(code)) {
            return Optional.empty();
        }
        var linkData = linkCodes.remove(code);
        if (linkData.getRight() >= System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(linkData.getLeft());
    }

    public static String generateLinkCode(UUID playerId) {

        if (linkCodes.size() >= PURGE_LIMIT) {
            purgeCodes();
        }

        long expiryTime = System.currentTimeMillis() + Config.INSTANCE.joining.linkCodeExpireMinutes * 60 * 1000;
        String code;
        do {
            code = String.valueOf(RANDOM.nextInt(100000, 1000000));  // 6 digit code
        }
        while (linkCodes.containsKey(code));

        linkCodes.put(code, new Pair<>(playerId, expiryTime));
        return code;
    }

    private static void purgeCodes() {
        long time = System.currentTimeMillis();

        linkCodes.entrySet().removeIf(entry -> entry.getValue().getRight() < time);
    }
}
