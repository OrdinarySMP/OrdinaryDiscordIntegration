package tronka.ordinarydiscordintegration.linking;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class LinkManager extends ListenerAdapter {
    private static final int PURGE_LIMIT = 30;
    private static final Random RANDOM = new Random();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, LinkRequest> linkRequests = new HashMap<>();
    private final LinkData linkData = JsonLinkData.from(FabricLoader.getInstance().getConfigDir().resolve(OrdinaryDiscordIntegration.ModId + ".player-links.json").toFile());
    private final OrdinaryDiscordIntegration integration;
    private final List<Role> requiredRoles;
    private final List<Role> joinRoles;


    public LinkManager(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        requiredRoles = Utils.parseRoleList(integration.getGuild(), integration.getConfig().joining.requiredRoles);
        joinRoles = Utils.parseRoleList(integration.getGuild(), integration.getConfig().joining.joinRoles);
    }

    public Optional<Member> getDiscordOf(UUID playerId) {
        var link = linkData.getPlayerLink(playerId);
        if (link.isPresent()) {
            var member = integration.getGuild().getMemberById(link.get().getDiscordId());
            if (member != null) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    public Optional<PlayerLink> getDataOf(long discordId) {
        return linkData.getPlayerLink(discordId);
    }

    public Optional<PlayerLink> getDataOf(UUID playerId) {
        return linkData.getPlayerLink(playerId);
    }

    public boolean canJoin(UUID playerId) {
        if (!integration.getConfig().joining.enableLinking) {return true;}
        var member = getDiscordOf(playerId);
        return member.filter(
                value -> Set.copyOf(value.getRoles()).containsAll(requiredRoles))
                .isPresent();
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        var memberOptional = getDiscordOf(player.getUuid());
        if (memberOptional.isEmpty()) {
            return;
        }
        var member = memberOptional.get();
        if (!PermissionUtil.canInteract(integration.getGuild().getSelfMember(), member)) {
            return;
        }
        if (integration.getConfig().joining.renameOnJoin
                && PermissionUtil.checkPermission(integration.getGuild().getSelfMember(), Permission.NICKNAME_MANAGE)) {
            member.modifyNickname(player.getName().getString()).queue();
        }
        if (PermissionUtil.checkPermission(integration.getGuild().getSelfMember(), Permission.MANAGE_ROLES)) {
            member.getGuild().modifyMemberRoles(member, joinRoles, Collections.emptyList()).queue();
        }
    }

    public String getJoinError(GameProfile profile) {
        var member = getDiscordOf(profile.getId());
        if (member.isEmpty()) {
            var code = generateLinkCode(profile);
            return integration.getConfig().strings.kickLinkCode.formatted(code);
        }
        return integration.getConfig().strings.kickMissingRoles;
    }

    public String confirmLink(long discordId, String code) {
        if (linkData.getPlayerLink(discordId).isPresent()) { return integration.getConfig().linkResults.failedTooManyLinked; }
        var linkRequest = getPlayerLinkFromCode(code);
        if (linkRequest.isEmpty()) { return integration.getConfig().linkResults.failedUnknownCode; }
        linkData.addPlayerLink(new PlayerLink(linkRequest.get(), discordId));
        return integration.getConfig().linkResults.linkSuccess
                .replace("%name%", linkRequest.get().getName());
    }

    private Optional<LinkRequest> getPlayerLinkFromCode(String code) {
        if (!linkRequests.containsKey(code)) {
            return Optional.empty();
        }
        var request = linkRequests.remove(code);
        if (request.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public String generateLinkCode(GameProfile profile) {
        if (linkRequests.size() >= PURGE_LIMIT) {
            purgeCodes();
        }

        long expiryTime = System.currentTimeMillis() + integration.getConfig().joining.linkCodeExpireMinutes * 60 * 1000;
        String code;
        do {
            code = String.valueOf(RANDOM.nextInt(100000, 1000000));  // 6 digit code
        }
        while (linkRequests.containsKey(code));
        linkRequests.put(code, new LinkRequest(profile.getId(), profile.getName(), expiryTime));
        return code;
    }

    private void purgeCodes() {
        linkRequests.entrySet().removeIf(request -> request.getValue().isExpired());
    }

    public void unlinkPlayers(List<Member> members) {
        if (!integration.getConfig().unlinkOnLeave) {
            return;
        }
        var memberSet = members.stream().map(Member::getIdLong).collect(Collectors.toSet());
        final int[] purgedCount = {0};
        linkData.getPlayerLinks().forEach(link -> {
            if (!memberSet.contains(link.getDiscordId())) {
                linkData.removePlayerLink(link);
                LOGGER.info("Removed link {} from members list", link.getDiscordId());
                purgedCount[0] += 1;
            }
        });
        if (purgedCount[0] != 0) {
            LOGGER.info("Purged {} linked players", purgedCount[0]);
        }
    }

    public void unlinkPlayer(long id) {
        linkData.getPlayerLink(id).ifPresent(linkData::removePlayerLink);
    }

}
