package tronka.ordinarydiscordintegration;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import tronka.ordinarydiscordintegration.linking.PlayerData;

import java.util.Objects;


public class DiscordCommandHandler extends ListenerAdapter {
    OrdinaryDiscordIntegration integration;
    public DiscordCommandHandler(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        integration.getJda().updateCommands().queue();
        integration.getGuild().updateCommands()
                .addCommands(
                        Commands.slash("link", "Link your minecraft with the code you got when joining")
                                .addOption(OptionType.STRING, "code", "Link code", true),
                        Commands.slash("linking", "Misc linking stuff")
                                .addSubcommands(new SubcommandData("get", "Retrieve linking information")
                                                .addOption(OptionType.USER, "user", "whose data to get")
                                                .addOption(OptionType.STRING, "mc-name", "minecraft username to get data"),
                                        new SubcommandData("unlink", "Unlink your account")
                                                .addOption(OptionType.USER, "user", "user to unlink")
                                                .addOption(OptionType.STRING, "mc-name", "minecraft account to unlink")
                                        ),
                        Commands.slash("list", "List the currently online players")
                ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || event.getMember().getGuild() != integration.getGuild()) { return; }
        switch (event.getName()) {
            case "link" -> {
                var code = event.getOptions().getFirst().getAsString();
                var message = integration.getLinkManager().confirmLink(event.getUser().getIdLong(), code);
                event.reply(message).setEphemeral(true).queue();
            }
            case "list" -> {
                var names = integration.getServer().getPlayerManager()
                        .getPlayerList().stream()
                        .filter(player -> !integration.getVanishIntegration().isVanished(player))
                        .map(player -> player.getName().getLiteralString());
                String message = String.join(", ", names.toList());
                if (message.isEmpty()) {
                    message = "There are currently no players online";
                }
                event.reply(message).setEphemeral(true).queue();
            }
            case "linking" -> {
                linkingCommand(event);
            }
        }
    }

    private void linkingCommand(SlashCommandInteractionEvent event) {
        if (event.getMember() == null) {
            event.reply("Invalid Context").setEphemeral(true).queue();
            return;
        }
        var target = event.getOption("user", OptionMapping::getAsMember);
        var minecraftName = event.getOption("mc-name", OptionMapping::getAsString);

        if (minecraftName != null) {
            if (PermissionUtil.checkPermission(event.getMember(), Permission.MODERATE_MEMBERS)) {
                event.deferReply().setEphemeral(true).queue();
                var profile = Utils.fetchProfile(minecraftName);
                linkingWithPlayer(event.getSubcommandName(), event.getHook(), profile);
                return;
            } else {
                event.reply("Insufficient permissions").setEphemeral(true).queue();
            }
            return;
        } else if (target == null) {
            target = event.getMember();
        }
        var isSelf = event.getMember().equals(target);
        if (!isSelf && PermissionUtil.checkPermission(event.getMember(), Permission.MODERATE_MEMBERS)) {
            event.reply("Insufficient permissions").setEphemeral(true).queue();
            return;
        }
        linkingWithMember(event, target);
    }

    // run with checked permissions
    private void linkingWithMember(SlashCommandInteractionEvent event, Member target) {
        var dataOptional = integration.getLinkManager().getDataOf(target.getIdLong());
        if (dataOptional.isEmpty()) {
            event.reply(target.getAsMention() + " is not linked to any player").setEphemeral(true).queue();
            return;
        }
        var data = dataOptional.get();
        if (Objects.equals(event.getSubcommandName(), "get")) {
            event.deferReply().setEphemeral(true).queue();
            var text = target.getAsMention() + " is linked to " + data.getPlayerName();
            if (data.altCount() != 0) {
                text += "\nwith " + data.altCount() + " alts: " + String.join(", ", data.getAlts().stream().map(PlayerData::getName).toList());
            }
            event.getHook().editOriginal(text).queue();
        } else if (Objects.equals(event.getSubcommandName(), "unlink")) {
            integration.getLinkManager().unlinkPlayer(target.getIdLong());
            event.reply("Successfully unlinked").setEphemeral(true).queue();
        }
    }

    // run with checked permissions
    private void linkingWithPlayer(String subCommand, InteractionHook hook, GameProfile profile) {
        if (profile == null) {
            hook.editOriginal("Could not find a player with that name").queue();
            return;
        }
        var data = integration.getLinkManager().getDataOf(profile.getId());
        if (data.isEmpty()) {
            hook.editOriginal("Could not find a linked account").queue();
            return;
        }
        if (Objects.equals(subCommand, "get")) {
            var member = integration.getLinkManager().getDiscordOf(data.get());
            if (member.isPresent()) {
                hook.editOriginal(profile.getName() + " is linked to " + member.get().getAsMention()).queue();
            } else {
                hook.editOriginal("Could not find a linked discord account").queue();
            }
        } else if (Objects.equals(subCommand, "unlink")) {
            integration.getLinkManager().unlinkPlayer(data.get());
            hook.editOriginal("Successfully unlinked").queue();
        }
    }
}
