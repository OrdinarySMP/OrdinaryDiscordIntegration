package tronka.justsync.linking;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import tronka.justsync.JustSyncApplication;
import tronka.justsync.Utils;
import tronka.justsync.config.Config;

public class LinkManager extends ListenerAdapter {

    private static final int PURGE_LIMIT = 30;
    private static final Random RANDOM = new Random();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, LinkRequest> linkRequests = new HashMap<>();
    private final JustSyncApplication integration;
    private LinkData linkData;
    private List<Role> requiredRoles;
    private List<Role> joinRoles;


    public LinkManager(JustSyncApplication integration) {
        this.integration = integration;
        integration.registerConfigReloadHandler(this::onConfigLoaded);
    }

    private void onConfigLoaded(Config config) {
        linkData = JsonLinkData.from(
            JustSyncApplication.getConfigFolder().resolve(JustSyncApplication.ModId + ".player-links.json").toFile());
        requiredRoles = Utils.parseRoleList(integration.getGuild(), integration.getConfig().linking.requiredRoles);
        joinRoles = Utils.parseRoleList(integration.getGuild(), integration.getConfig().linking.joinRoles);
    }

    public Optional<Member> getDiscordOf(UUID playerId) {
        Optional<PlayerLink> link = linkData.getPlayerLink(playerId);
        if (link.isPresent()) {
            return getDiscordOf(link.get());
        }
        return Optional.empty();
    }

    public boolean isAllowedToJoin(Member member) {
        if (!integration.getConfig().linking.enableLinking) {
            return true;
        }
        return Set.copyOf(member.getRoles()).containsAll(requiredRoles);
    }

    public boolean isAllowedToJoin(long discordId) {
        return isAllowedToJoin(integration.getGuild().getMemberById(discordId));
    }

    public Optional<Member> getDiscordOf(PlayerLink link) {
        Member member = integration.getGuild().getMemberById(link.getDiscordId());
        if (member != null) {
            return Optional.of(member);
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
        if (!integration.getConfig().linking.enableLinking) {
            return true;
        }
        Optional<Member> member = getDiscordOf(playerId);
        if (member.isEmpty()) {
            return false;
        }
        if (integration.getConfig().linking.disallowTimeoutMembersToJoin && member.get().isTimedOut()) {
            return false;
        }
        return Set.copyOf(member.get().getRoles()).containsAll(requiredRoles);
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        Optional<PlayerLink> dataOptional = linkData.getPlayerLink(player.getUuid());
        if (dataOptional.isEmpty()) {
            return;
        }
        PlayerLink data = dataOptional.get();
        Optional<Member> memberOptional = getDiscordOf(data);
        if (memberOptional.isEmpty()) {
            return;
        }
        Member member = memberOptional.get();
        if (!PermissionUtil.canInteract(integration.getGuild().getSelfMember(), member)) {
            return;
        }

        if (data.getPlayerId().equals(player.getUuid()) && integration.getConfig().linking.renameOnJoin
            && PermissionUtil.checkPermission(integration.getGuild().getSelfMember(), Permission.NICKNAME_MANAGE)) {
            member.modifyNickname(player.getName().getString()).queue();
        }
        if (PermissionUtil.checkPermission(integration.getGuild().getSelfMember(), Permission.MANAGE_ROLES)) {
            member.getGuild().modifyMemberRoles(member, joinRoles, Collections.emptyList()).queue();
        }
    }

    public String getJoinError(GameProfile profile) {
        Optional<Member> member = getDiscordOf(profile.getId());
        if (member.isEmpty()) {
            String code = generateLinkCode(profile);
            return integration.getConfig().kickMessages.kickLinkCode.formatted(code);
        }
        if (member.get().isTimedOut()) {
            return integration.getConfig().kickMessages.kickTimedOut;
        }
        return integration.getConfig().kickMessages.kickMissingRoles;
    }

    public String confirmLink(long discordId, String code) {
        if (!isAllowedToJoin(discordId)) {
            return integration.getConfig().linkResults.linkNotAllowed;
        }
        Optional<LinkRequest> linkRequest = getPlayerLinkFromCode(code);
        if (linkRequest.isEmpty()) {
            return integration.getConfig().linkResults.failedUnknownCode;
        }
        Optional<PlayerLink> existing = linkData.getPlayerLink(discordId);
        if (existing.isPresent()) {
            PlayerLink link = existing.get();
            if (link.altCount() >= integration.getConfig().linking.maxAlts) {
                return integration.getConfig().linkResults.failedTooManyLinked;
            }
            link.addAlt(PlayerData.from(linkRequest.get()));
            integration.getLuckPermsIntegration().setAlt(linkRequest.get().getPlayerId());
        } else {
            linkData.addPlayerLink(new PlayerLink(linkRequest.get(), discordId));
        }
        return integration.getConfig().linkResults.linkSuccess.replace("%name%", linkRequest.get().getName());
    }

    private Optional<LinkRequest> getPlayerLinkFromCode(String code) {
        if (!linkRequests.containsKey(code)) {
            return Optional.empty();
        }
        LinkRequest request = linkRequests.remove(code);
        if (request.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public String generateLinkCode(GameProfile profile) {
        if (linkRequests.size() >= PURGE_LIMIT) {
            purgeCodes();
        }

        long expiryTime =
            System.currentTimeMillis() + integration.getConfig().linking.linkCodeExpireMinutes * 60 * 1000;
        String code;
        do {
            code = String.valueOf(RANDOM.nextInt(100000, 1000000));  // 6-digit code
        } while (linkRequests.containsKey(code));
        linkRequests.put(code, new LinkRequest(profile.getId(), profile.getName(), expiryTime));
        return code;
    }

    private void purgeCodes() {
        linkRequests.entrySet().removeIf(request -> request.getValue().isExpired());
    }

    public void unlinkPlayers(List<Member> members) {
        if (!integration.getConfig().linking.unlinkOnLeave) {
            return;
        }
        Set<Long> memberSet = members.stream().map(Member::getIdLong).collect(Collectors.toSet());
        List<PlayerLink> toRemove = linkData.getPlayerLinks().filter(link -> !memberSet.contains(link.getDiscordId()))
            .toList();
        toRemove.forEach(linkData::removePlayerLink);
        if (!toRemove.isEmpty()) {
            LOGGER.info("Purged {} linked players", toRemove.size());
        }
    }

    public void unlinkPlayer(long id) {
        linkData.getPlayerLink(id).ifPresent(this::unlinkPlayer);
    }

    public void unlinkPlayer(UUID uuid) {
        Optional<PlayerLink> dataOptional = linkData.getPlayerLink(uuid);
        if (dataOptional.isEmpty()) {
            return;
        }
        PlayerLink data = dataOptional.get();
        if (data.getPlayerId().equals(uuid)) {
            linkData.removePlayerLink(data);
        } else {
            data.removeAlt(uuid);
        }
    }

    public void unlinkPlayer(PlayerLink link) {
        MinecraftServer server = integration.getServer();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(link.getPlayerId());
        if (player != null) {
            player.networkHandler.disconnect(Text.of(integration.getConfig().kickMessages.kickUnlinked));
        }
        for (PlayerData alt : link.getAlts()) {
            ServerPlayerEntity altPlayer = server.getPlayerManager().getPlayer(alt.getId());
            if (altPlayer != null) {
                altPlayer.networkHandler.disconnect(Text.of(integration.getConfig().kickMessages.kickUnlinked));
            }
        }
        linkData.removePlayerLink(link);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Member member = event.getMember();
        if (member == null) {
            return;
        }

        kickAccounts(member, integration.getConfig().kickMessages.kickOnLeave);
        unlinkPlayer(member.getIdLong());
        LOGGER.info("Removed link of \"{}\" because they left the guild.", member.getEffectiveName());
    }

    public void kickAccounts(Member member, String reason) {
        Optional<PlayerLink> playerLink = integration.getLinkManager().getDataOf(member.getIdLong());
        if (playerLink.isEmpty()) {
            return;
        }
        MinecraftServer server = integration.getServer();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerLink.get().getPlayerId());
        if (player != null) {
            player.networkHandler.disconnect(Text.of(reason));
        }

        for (PlayerData alt : playerLink.get().getAlts()) {
            ServerPlayerEntity altPlayer = server.getPlayerManager().getPlayer(alt.getId());
            if (altPlayer != null) {
                altPlayer.networkHandler.disconnect(Text.of(reason));
            }
        }
    }
}
