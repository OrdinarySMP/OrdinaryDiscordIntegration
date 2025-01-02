package tronka.ordinarydiscordintegration;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ChatBridge extends ListenerAdapter {
    private final OrdinaryDiscordIntegration integration;
    private TextChannel channel;
    private Webhook webhook;
    private static final String webhookId = "odi-bridge-hook";
    private boolean stopped = false;
    private ServerPlayerEntity lastMessageSender;
    private String lastMessage;
    private int repeatedCount = 0;

    public ChatBridge(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        ServerMessageEvents.CHAT_MESSAGE.register(this::onMcChatMessage);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        integration.registerConfigReloadHandler(this::onConfigLoaded);
        channel.sendMessage(integration.getConfig().messages.startMessage).queue();
    }

    private void onConfigLoaded(Config config) {
        channel = Utils.getTextChannel(integration.getJda(), config.serverChatChannel);
        if (integration.getConfig().useWebHooks) {
            channel.retrieveWebhooks().onSuccess((webhooks -> {
                Optional<Webhook> hook = webhooks.stream().filter(w -> w.getOwner() == this.integration.getGuild().getSelfMember()).findFirst();
                if (hook.isPresent()) {
                    webhook = hook.get();
                } else {
                    channel.createWebhook(webhookId).onSuccess(w -> webhook = w).queue();
                }
            })).queue();
        }

    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // discord message
        if (event.getChannel() != channel) {
            return;
        }
        if (event.getMember() == null || event.getAuthor().isBot()) {
            return;
        }

        MutableText message = Text.literal(integration.getConfig().messages.chatMessageFormat
                        .replace("%user%", event.getMember().getEffectiveName())
                        .replace("%msg%", event.getMessage().getContentDisplay()));

        List<Message.Attachment> attachments = event.getMessage().getAttachments();
        if (!attachments.isEmpty()) {
            message.append(Text.literal("\nAttachments:"));
            for (Message.Attachment attachment : attachments) {
                MutableText attachmentText = Text.literal("\n" + attachment.getFileName()).setStyle(Style.EMPTY
                        .withUnderline(true)
                        .withColor(integration.getConfig().messages.chatMessageAttachmentColor)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, attachment.getUrl())));
                message.append(attachmentText);
            }
        }

        sendMcChatMessage(message);
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        channel.sendMessage(
                integration.getConfig().messages.playerJoinMessage
                        .replace("%user%", player.getName().getString())
        ).queue();
        updateRichPresence(1);
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (stopped) {
            return;
        }
        channel.sendMessage(
                integration.getConfig().messages.playerLeaveMessage
                        .replace("%user%", player.getName().getString())
        ).queue();
        updateRichPresence(-1);
    }

    private void updateRichPresence(int modifier) {
        if (!integration.getConfig().showPlayerCountStatus) {
            return;
        }
        long playerCount = integration.getServer().getPlayerManager().getPlayerList().stream()
                .filter(p -> !integration.getVanishIntegration().isVanished(p)).count() + modifier;
        integration.getJda().getPresence().setPresence(
                Activity.playing(switch ((int) playerCount) {
                    case 0 -> integration.getConfig().messages.onlineCountZero;
                    case 1 -> integration.getConfig().messages.onlineCountSingular;
                    default -> integration.getConfig().messages.onlineCountPlural.formatted(playerCount);
                }),
                false);
    }

    public void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        if (integration.getConfig().broadCastDeathMessages) {
            channel.sendMessage(
                    source.getDeathMessage(player).getString()
            ).queue();
        }
    }

    public void onReceiveAdvancement(ServerPlayerEntity player, AdvancementDisplay advancement){
        if(integration.getConfig().announceAdvancements) {
            channel.sendMessage(
                    integration.getConfig().messages.advancementMessage
                            .replace("%user%", player.getName().getString())
                            .replace("%title%", advancement.getTitle().getString())
                            .replace("%description%", advancement.getDescription().getString())
            ).queue();
        }
    }

    public void sendMcChatMessage(Text message) {
        integration.getServer().getPlayerManager().broadcast(message, false);
    }

    private void onServerStopping(MinecraftServer minecraftServer) {
        channel.sendMessage(integration.getConfig().messages.stopMessage).queue();
        stopped = true;
    }


    private void onMcChatMessage(SignedMessage signedMessage, ServerPlayerEntity player, MessageType.Parameters parameters) {
        String message = signedMessage.getContent().getLiteralString();
        if (integration.getConfig().stackMessages && lastMessageSender == player && Objects.equals(message, lastMessage)) {
            repeatedCount++;
            return;
        } else if(repeatedCount > 0) {
            String displayCounter = repeatedCount > 1 ? " (" + repeatedCount + ")" : "";
            String updatedLastMessage = lastMessage + displayCounter;
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
            String formattedMessage = sender.getName() + ": " + message;
            channel.sendMessage(formattedMessage).queue();
        }
    }

    private String getAvatarUrl(ServerPlayerEntity player) {
        return integration.getConfig().avatarUrl
                .replace("%UUID%", player.getUuid().toString())
                .replace("%randomUUID%", UUID.randomUUID().toString());
    }

    private void sendAsWebhook(String message, ServerPlayerEntity player) {
        try(JDAWebhookClient client = JDAWebhookClient.from(webhook)) {
            String avatarUrl = getAvatarUrl(player);
            WebhookMessage msg = new WebhookMessageBuilder()
                    .setUsername(player.getName().getLiteralString())
                    .setAvatarUrl(avatarUrl)
                    .setContent(message)
                    .build();
            client.send(msg);
        }
    }
}
