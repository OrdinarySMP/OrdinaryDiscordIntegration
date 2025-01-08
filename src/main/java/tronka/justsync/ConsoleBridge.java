package tronka.justsync;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import net.minecraft.server.command.ServerCommandSource;
import tronka.justsync.config.Config;

public class ConsoleBridge extends ListenerAdapter {

    private final JustSyncApplication integration;
    private TextChannel channel;
    private Role opRole;
    private List<LogRedirect> logRedirects;

    public ConsoleBridge(JustSyncApplication integration) {
        this.integration = integration;
        integration.registerConfigReloadHandler(this::onConfigLoaded);
    }

    private void onConfigLoaded(Config config) {
        channel = Utils.getTextChannel(integration.getJda(), config.commands.consoleChannel);
        String opRoleId = integration.getConfig().commands.opRole;
        if (channel != null && !opRoleId.isEmpty()) {
            opRole = channel.getGuild().getRoleById(opRoleId);
        } else {
            opRole = null;
        }
        logRedirects = new ArrayList<>();
        for (Config.LogRedirectChannel logRedirectChannel : config.commands.logRedirectChannels) {
            TextChannel channel = Utils.getTextChannel(integration.getJda(), logRedirectChannel.channel);
            if (channel != null) {
                logRedirects.add(new LogRedirect(channel, logRedirectChannel.redirectPrefixes));
            } else {
                LogUtils.getLogger()
                    .info("Could not load log redirect: ID: \"{}\", redirects: [{}]", logRedirectChannel.channel,
                        String.join(", ", logRedirectChannel.redirectPrefixes));
            }
        }
    }

    public void onCommandExecute(ServerCommandSource source, String command) {
        if (channel == null) {
            return;
        }
        if (!integration.getConfig().commands.logCommandsInConsole) {
            return;
        }

        if (source.getEntity() == null && !source.getName().equals("Server")
            && !integration.getConfig().commands.logCommandBlockCommands) {
            return;
        }

        if (Utils.startsWithAny(command, integration.getConfig().commands.ignoredCommands)) {
            return;
        }
        TextChannel target = channel;
        for (LogRedirect redirect : logRedirects) {
            if (Utils.startsWithAny(command, redirect.prefixes)) {
                target = redirect.channel;
                break;
            }
        }
        target.sendMessage(integration.getConfig().messages.commandExecutedInfoText.replace("%user%", source.getName())
            .replace("%cmd%", command)).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel() != channel || event.getMember() == null) {
            return;
        }

        String message = event.getMessage().getContentStripped();
        if (!message.startsWith(integration.getConfig().commands.commandPrefix)) {
            return;
        }

        if (!PermissionUtil.checkPermission(event.getMember(), Permission.ADMINISTRATOR) && !event.getMember()
            .getRoles().contains(opRole)) {
            event.getChannel().sendMessage("You don't have permission to use this command").queue();
            return;
        }
        message = message.substring(integration.getConfig().commands.commandPrefix.length());
        if (message.equals("help")) {
            EmbedBuilder embed = new EmbedBuilder();
            integration.getConfig().commands.commandList.forEach(
                command -> embed.addField(command.commandName, command.inGameAction.replace("%args%", "<args>"), true));
            event.getChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }
        String[] commandParts = message.split(" ", 2);
        String commandName = commandParts[0].toLowerCase();
        String commandArgs = commandParts.length == 2 ? commandParts[1] : "";
        Optional<Config.BridgeCommand> commandOptional = integration.getConfig().commands.commandList.stream()
            .filter(cmd -> cmd.commandName.equals(commandName)).findFirst();
        if (commandOptional.isPresent()) {
            Config.BridgeCommand command = commandOptional.get();
            DiscordCommandSender commandSender = new DiscordCommandSender(integration.getServer(),
                event.getAuthor().getAsMention(), feedback -> {
                if (feedback.length() > 2000) {
                    feedback = feedback.substring(0, 2000);
                }
                event.getChannel().sendMessage(feedback).queue();
            });
            String inGameCommand = command.inGameAction.replace("%args%", commandArgs);
            try {
                integration.getServer().getCommandManager().getDispatcher().execute(inGameCommand, commandSender);
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            event.getChannel().sendMessage("Unknown command: \"" + commandName + "\"").queue();
        }
    }

    private record LogRedirect(TextChannel channel, List<String> prefixes) {

    }
}
