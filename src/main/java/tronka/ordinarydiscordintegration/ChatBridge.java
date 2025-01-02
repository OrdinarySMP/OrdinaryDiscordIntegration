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
import java.util.concurrent.TimeUnit;

public class ChatBridge extends ListenerAdapter {
    private static final int DELAY_MS = 20;
    private final OrdinaryDiscordIntegration integration;
    private TextChannel channel;
    private Webhook webhook;
    private static final String webhookId = "odi-bridge-hook";
    private boolean stopped = false;
    private ServerPlayerEntity lastMessageSender;
    private String lastMessage;
    private int repeatedCount = 0;
    private JDAWebhookClient webhookClient;

    public ChatBridge(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        ServerMessageEvents.CHAT_MESSAGE.register(this::onMcChatMessage);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        integration.registerConfigReloadHandler(this::onConfigLoaded);
        channel.sendMessage(integration.getConfig().messages.startMessage).queue();
    }

    private void onConfigLoaded(Config config) {
        channel = Utils.getTextChannel(integration.getJda(), config.serverChatChannel);
        setWebhook(null);
        if (integration.getConfig().useWebHooks) {
            channel.retrieveWebhooks().onSuccess((webhooks -> {
                Optional<Webhook> hook = webhooks.stream().filter(w -> w.getOwner() == this.integration.getGuild().getSelfMember()).findFirst();
                if (hook.isPresent()) {
                    setWebhook(hook.get());
                } else {
                    channel.createWebhook(webhookId).onSuccess(this::setWebhook).queue();
                }
            })).queue();
        }

    }

    private void setWebhook(Webhook webhook) {
        this.webhook = webhook;
        if (webhookClient != null) {
            webhookClient.close();
            webhookClient = null;
        }
        if (webhook != null) {
            webhookClient = JDAWebhookClient.from(webhook);
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

        Message repliedMessage = event.getMessage().getReferencedMessage();
        MutableText message;
        if (repliedMessage != null) {
            message = Text.literal(integration.getConfig().messages.chatMessageFormatReply
                    .replace("%user%", event.getMember().getEffectiveName())
                    .replace("%userRepliedTo%", repliedMessage.getMember() != null
                            ? repliedMessage.getMember().getEffectiveName()
                            : repliedMessage.getAuthor().getEffectiveName())
                    .replace("%msg%", event.getMessage().getContentDisplay()));
        } else {
            message = Text.literal(integration.getConfig().messages.chatMessageFormat
                        .replace("%user%", event.getMember().getEffectiveName())
                        .replace("%msg%", event.getMessage().getContentDisplay()));
        }

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
        sendStackedMessage(
                integration.getConfig().messages.playerJoinMessage
                        .replace("%user%", player.getName().getString()),
                null
        );
        updateRichPresence(1);
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (stopped) {
            return;
        }

        sendStackedMessage(integration.getConfig().messages.playerLeaveMessage
                .replace("%user%", player.getName().getString()),
                null);
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
            String message = source.getDeathMessage(player).getString();
            sendStackedMessage(message, null);
        }
    }

    public void onReceiveAdvancement(ServerPlayerEntity player, AdvancementDisplay advancement){
        if(integration.getConfig().announceAdvancements) {
            sendStackedMessage(
                    integration.getConfig().messages.advancementMessage
                            .replace("%user%", player.getName().getString())
                            .replace("%title%", advancement.getTitle().getString())
                            .replace("%description%", advancement.getDescription().getString()),
                    null);
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
        sendStackedMessage(message, player);
//        if (integration.getConfig().stackMessages && lastMessageSender == player && Objects.equals(message, lastMessage)) {
//            repeatedCount++;
//            return;
//        } else if(repeatedCount > 0) {
//            String displayCounter = repeatedCount > 1 ? " (" + repeatedCount + ")" : "";
//            String updatedLastMessage = lastMessage + displayCounter;
//            sendPlayerMessageToDiscord(updatedLastMessage, lastMessageSender);
//        }
//
//        sendPlayerMessageToDiscord(message, player);
//
//        lastMessageSender = player;
//        lastMessage = signedMessage.getContent().getLiteralString();
//        repeatedCount = 0;
    }

    private void sendStackedMessage(String message, ServerPlayerEntity sender) {
        boolean shouldDelay = false;
        if (integration.getConfig().stackMessages){
            if (lastMessageSender == sender && message.equals(lastMessage)) {
                repeatedCount++;
                return;
            } else if (repeatedCount > 0) {
                String displayCounter = repeatedCount > 1 ? " (" + repeatedCount + ")" : "";
                String updatedLastMessage = lastMessage + displayCounter;
                sendChatMessageToDiscord(updatedLastMessage, lastMessageSender, false);
                repeatedCount = 0;
                shouldDelay = true;
            }
        }
        sendChatMessageToDiscord(message, sender, shouldDelay);
        lastMessageSender = sender;
        lastMessage = message;
    }

    private void sendChatMessageToDiscord(String message, ServerPlayerEntity sender, boolean shouldDelay) {
        if (sender == null) {
            sendMiscMessageToDiscord(message, shouldDelay);
            return;
        }
        sendPlayerMessageToDiscord(message, sender, shouldDelay);
    }

    private void sendMiscMessageToDiscord(String message, boolean shouldDelay) {
        channel.sendMessage(message).queueAfter(shouldDelay ? DELAY_MS : 0, TimeUnit.MILLISECONDS);
    }

    private void sendPlayerMessageToDiscord(String message, ServerPlayerEntity sender, boolean shouldDelay) {
        if (webhook != null) {
            sendAsWebhook(message, sender);
        } else {
            String formattedMessage = sender.getName() + ": " + message;
            channel.sendMessage(formattedMessage).queueAfter(shouldDelay ? DELAY_MS : 0, TimeUnit.MILLISECONDS);
        }
    }

    private String getAvatarUrl(ServerPlayerEntity player) {
        return integration.getConfig().avatarUrl
                .replace("%UUID%", player.getUuid().toString())
                .replace("%randomUUID%", UUID.randomUUID().toString());
    }

    private void sendAsWebhook(String message, ServerPlayerEntity player) {
        String avatarUrl = getAvatarUrl(player);
        WebhookMessage msg = new WebhookMessageBuilder()
                .setUsername(player.getName().getLiteralString())
                .setAvatarUrl(avatarUrl)
                .setContent(message)
                .build();
        webhookClient.send(msg);
    }
}
