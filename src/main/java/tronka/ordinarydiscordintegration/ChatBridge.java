package tronka.ordinarydiscordintegration;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Objects;
import java.util.UUID;

public class ChatBridge extends ListenerAdapter {
    private final OrdinaryDiscordIntegration integration;
    private final TextChannel channel;
    private Webhook webhook;
    private static final String webhookId = "odi-bridge-hook";
    private boolean stopped = false;
    private ServerPlayerEntity lastMessageSender;
    private String lastMessage;
    private int repeatedCount = 0;

    public ChatBridge(OrdinaryDiscordIntegration integration, TextChannel serverChatChannel) {
        this.integration = integration;
        this.channel = serverChatChannel;
        ServerMessageEvents.CHAT_MESSAGE.register(this::onMcChatMessage);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // get webhook
        if (integration.getConfig() .useWebHooks) {
            channel.retrieveWebhooks().onSuccess((webhooks -> {
                var hook = webhooks.stream().filter(w -> w.getOwner() == this.integration.getGuild().getSelfMember()).findFirst();
                if (hook.isPresent()) {
                    webhook = hook.get();
                } else {
                    channel.createWebhook(webhookId).onSuccess(w -> webhook = w).queue();
                }
            })).queue();
        }

        channel.sendMessage(integration.getConfig().messages.startMessage).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // discord message
        if (event.getChannel() != channel) {
            return;
        }
        if (event.getAuthor().isBot()) {
            return;
        }
        var message = Text.of(
                integration.getConfig().messages.chatMessageFormat
                        .replace("%user%", event.getAuthor().getName())
                        .replace("%msg%", event.getMessage().getContentDisplay())
        );
        sendMcChatMessage(message);
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        channel.sendMessage(
                integration.getConfig().messages.playerJoinMessage
                        .replace("%user%", player.getName().getString())
        ).queue();
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (stopped) {
            return;
        }
        channel.sendMessage(
                integration.getConfig().messages.playerLeaveMessage
                        .replace("%user%", player.getName().getString())
        ).queue();
    }

    public void sendMcChatMessage(Text message) {
        for (var player : integration.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(message);
        }
    }

    private void onServerStopping(MinecraftServer minecraftServer) {
        channel.sendMessage(integration.getConfig().messages.stopMessage).queue();
        stopped = true;
    }


    private void onMcChatMessage(SignedMessage signedMessage, ServerPlayerEntity player, MessageType.Parameters parameters) {
        var message = signedMessage.getContent().getLiteralString();
        if (integration.getConfig().stackMessages && lastMessageSender == player && Objects.equals(message, lastMessage)) {
            repeatedCount++;
            return;
        } else if(repeatedCount > 0) {
            var displayCounter = repeatedCount > 1 ? " (" + repeatedCount + ")" : "";
            var updatedLastMessage = lastMessage + displayCounter;
            sendDiscordMessage(updatedLastMessage, lastMessageSender);
        }

        sendDiscordMessage(message, player);

        lastMessageSender = player;
        lastMessage = signedMessage.getContent().getLiteralString();
        repeatedCount = 0;
    }

    private void sendDiscordMessage(String message, ServerPlayerEntity sender) {
        if (webhook != null) {
            sendAsWebhook(message, sender);
        } else {
            var formattedMessage = sender.getName() + ": " + message;
            channel.sendMessage(formattedMessage).queue();
        }
    }

    private String getAvatarUrl(ServerPlayerEntity player) {
        return integration.getConfig().avatarUrl
                .replace("%UUID%", player.getUuid().toString())
                .replace("%randomUUID%", UUID.randomUUID().toString());
    }

    private void sendAsWebhook(String message, ServerPlayerEntity player) {
        try(var client = JDAWebhookClient.from(webhook)) {
            var avatarUrl = getAvatarUrl(player);
            var msg = new WebhookMessageBuilder()
                    .setUsername(player.getName().getLiteralString())
                    .setAvatarUrl(avatarUrl)
                    .setContent(message)
                    .build();
            client.send(msg);
        }
    }
}
