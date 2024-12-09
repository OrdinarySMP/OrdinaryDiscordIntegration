package tronka.ordinarydiscordintegration;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import net.minecraft.server.network.ServerPlayerEntity;

public class ConsoleBridge extends ListenerAdapter {
    private final OrdinaryDiscordIntegration integration;
    private final TextChannel channel;
    private final Role opRole;
    public ConsoleBridge(OrdinaryDiscordIntegration integration, TextChannel consoleChannel) {
        this.integration = integration;
        this.channel = consoleChannel;
        var opRoleId =  integration.getConfig().commands.opRole;
        if (channel != null && !opRoleId.isEmpty()) {
            opRole = channel.getGuild().getRoleById(opRoleId);
        } else {
            opRole = null;
        }
    }

    public void onCommandExecute(String command, ServerPlayerEntity player) {
        if (channel == null) {
            return;
        }
        if (!integration.getConfig().commands.showCommandsInConsole) {
            return;
        }
        if (Utils.startsWithAny(command, integration.getConfig().commands.ignoredCommands)) {
            return;
        }
        channel.sendMessage(
                integration.getConfig().messages.commandExecutedInfoText
                        .replace("%user%", player.getName().getString())
                        .replace("%msg%", command)
        ).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel() != channel || event.getMember() == null) {
            return;
        }

        var message = event.getMessage().getContentStripped();
        if (!message.startsWith(integration.getConfig().commands.commandPrefix)) {
            return;
        }

        if (!PermissionUtil.checkPermission(event.getMember(), Permission.ADMINISTRATOR) && !event.getMember().getRoles().contains(opRole)) {
            event.getChannel().sendMessage("You don't have permission to use this command").queue();
            return;
        }
        message = message.substring(integration.getConfig().commands.commandPrefix.length());
        if (message.equals("help")) {
            var embed = new EmbedBuilder();
            integration.getConfig().commands.commands.forEach(command -> embed.addField(command.commandName, command.inGameAction.replace("%args%", "<args>"), true));
            event.getChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }
        var commandParts = message.split(" ", 2);
        var commandName = commandParts[0].toLowerCase();
        var commandArgs = commandParts.length == 2 ? commandParts[1] : "";
        var commandOptional = integration.getConfig().commands.commands.stream().filter(cmd -> cmd.commandName.equals(commandName)).findFirst();
        if (commandOptional.isPresent()) {
            var command = commandOptional.get();
            var commandSender = new DiscordCommandSender(integration.getServer(), event.getAuthor().getAsMention(), feedback -> {
                if (feedback.length() > 2000) {
                    feedback = feedback.substring(0, 2000);
                }
                event.getChannel().sendMessage(feedback).queue();
            });
            var inGameCommand = command.inGameAction.replace("%args%", commandArgs);
            try {
                integration.getServer().getCommandManager().getDispatcher().execute(inGameCommand, commandSender);
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            event.getChannel().sendMessage("Unknown command: \"" + commandName + "\"").queue();
        }
    }
}
