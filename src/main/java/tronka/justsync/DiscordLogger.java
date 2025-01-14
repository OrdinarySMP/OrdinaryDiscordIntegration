package tronka.justsync;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import tronka.justsync.config.Config;
import tronka.justsync.linking.PlayerData;
import tronka.justsync.linking.PlayerLink;

public class DiscordLogger {

    private final JustSyncApplication integration;
    private TextChannel channel;

    public DiscordLogger(JustSyncApplication integration) {
        this.integration = integration;
        integration.registerConfigReloadHandler(this::onConfigLoaded);
    }

    private void onConfigLoaded(Config config) {
        if (this.integration.getConfig().linking.logLinking) {
            this.channel = Utils.getTextChannel(this.integration.getJda(), config.linking.linkingLogChannel);
            if (this.channel == null) {
                LogUtils.getLogger().error("invalid linkingLogChannel id");
            }
        } else {
            this.channel = null;
        }
    }

    public void onLinkMain(UUID uuid) {
        if (this.channel == null) {
            return;
        }
        Optional<PlayerLink> optionalPlayerLink = this.integration.getLinkManager().getDataOf(uuid);
        if (optionalPlayerLink.isEmpty()) {
            LogUtils.getLogger().error("playerlink of just linked player not found");
            return;
        }
        this.channel.sendMessage(
            "`" + optionalPlayerLink.get().getPlayerName() + "` was linked to <@" + optionalPlayerLink.get()
                .getDiscordId() + ">").queue();

    }

    public void onLinkAlt(UUID uuid) {
        if (this.channel == null) {
            return;
        }
        Optional<PlayerLink> optionalPlayerLink = this.integration.getLinkManager().getDataOf(uuid);
        if (optionalPlayerLink.isEmpty()) {
            LogUtils.getLogger().error("something went wrong, playerlink not found after linking");
            return;
        }
        Optional<PlayerData> alt = optionalPlayerLink.get().getAlts().stream()
            .filter((playerData) -> playerData.getId() == uuid).findFirst();
        if (alt.isEmpty()) {
            LogUtils.getLogger().error("alt that was just added not found in playerlink");
            return;
        }
        this.channel.sendMessage(
            "Alt `" + alt.get().getName() + "` was added to `" + optionalPlayerLink.get().getPlayerName() + "` aka <@"
                + optionalPlayerLink.get().getDiscordId() + ">").queue();
    }

    public void onUnlink(PlayerLink playerLink) {
        if (this.channel == null) {
            return;
        }
        long discordId = playerLink.getDiscordId();
        String mcUsername = playerLink.getPlayerName();
        List<PlayerData> alts = playerLink.getAlts();
        StringBuilder altsList = new StringBuilder();
        if (alts.isEmpty()) {
            altsList.append("with no alts");
        } else {
            altsList.append("with following alts: `");
            for (PlayerData alt : alts) {
                altsList.append(alt.getName()).append("`, ");
            }
            altsList.delete(altsList.length() - 2, altsList.length() - 1);
        }
        this.channel.sendMessage("`" + mcUsername + "` was unlinked from <@" + discordId + "> " + altsList).queue();
    }

    public void onUnlinkAlt(UUID uuid) {
        if (this.channel == null) {
            return;
        }
        Optional<PlayerLink> optionalPlayerLink = this.integration.getLinkManager().getDataOf(uuid);
        if (optionalPlayerLink.isEmpty()) {
            LogUtils.getLogger().error("tried to unlink alt, but alt was not found");
            return;
        }
        this.channel.sendMessage(
            "Alt `" + Utils.getPlayerName(uuid) + "` was unlinked from user `" + optionalPlayerLink.get()
                .getPlayerName() + "` aka <@" + optionalPlayerLink.get().getDiscordId() + ">").queue();
    }

}
