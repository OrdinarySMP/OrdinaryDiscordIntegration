package tronka.ordinarydiscordintegration;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.minecraft.server.network.ServerPlayerEntity;

public class ConsoleBridge extends ListenerAdapter {
    private final OrdinaryDiscordIntegration integration;
    private final TextChannel channel;
    public ConsoleBridge(OrdinaryDiscordIntegration integration, TextChannel consoleChannel) {
        this.integration = integration;
        this.channel = consoleChannel;
    }

    public void onCommandExecute(String command, ServerPlayerEntity player) {
        if (channel == null) {
            return;
        }
        if (!integration.getConfig().showCommandsInConsole) {
            return;
        }
        channel.sendMessage(
                integration.getConfig().messages.commandExecutedInfoText
                        .replace("%user%", player.getName().getString())
                        .replace("%msg%", command)
        ).queue();
    }
}
