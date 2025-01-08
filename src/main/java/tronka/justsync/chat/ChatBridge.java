package tronka.justsync.chat;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import eu.pb4.placeholders.api.node.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import tronka.justsync.JustSyncApplication;
import tronka.justsync.Utils;
import tronka.justsync.config.Config;

public class ChatBridge extends ListenerAdapter {

    private static final String webhookId = "discord-js-hook";
    private final JustSyncApplication integration;
    private TextChannel channel;
    private boolean stopped = false;
    private DiscordChatMessageSender messageSender;
    private JDAWebhookClient webhookClient;

    public ChatBridge(JustSyncApplication integration) {
        this.integration = integration;
        ServerMessageEvents.CHAT_MESSAGE.register(this::onMcChatMessage);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        integration.registerConfigReloadHandler(this::onConfigLoaded);
        channel.sendMessage(integration.getConfig().messages.startMessage).queue();
    }

    private void onConfigLoaded(Config config) {
        channel = Utils.getTextChannel(integration.getJda(), config.serverChatChannel);
        this.messageSender = null;
        setWebhook(null);
        if (integration.getConfig().useWebHooks) {
            channel.retrieveWebhooks().onSuccess((webhooks -> {
                Optional<Webhook> hook = webhooks.stream()
                    .filter(w -> w.getOwner() == this.integration.getGuild().getSelfMember()).findFirst();
                if (hook.isPresent()) {
                    setWebhook(hook.get());
                } else {
                    channel.createWebhook(webhookId).onSuccess(this::setWebhook).queue();
                }
            })).queue();
        }
        this.updateRichPresence(0);
    }

    private void setWebhook(Webhook webhook) {
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

        String baseText = repliedMessage == null ? integration.getConfig().messages.chatMessageFormat
            : integration.getConfig().messages.chatMessageFormatReply;

        TextNode attachmentInfo;
        if (!event.getMessage().getAttachments().isEmpty()) {
            List<TextNode> attachments = new ArrayList<>(List.of(TextNode.of("\nAttachments:")));
            for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                attachments.add(
                    TextReplacer.create().replace("link", attachment.getUrl()).replace("name", attachment.getFileName())
                        .applyNode(integration.getConfig().messages.attachmentFormat));
            }
            attachmentInfo = TextNode.wrap(attachments);
        } else {
            attachmentInfo = TextNode.empty();
        }

        String replyUser = repliedMessage == null ? "%userRepliedTo%"
            : (repliedMessage.getMember() == null ? repliedMessage.getAuthor().getEffectiveName()
                : repliedMessage.getMember().getEffectiveName());
        sendMcChatMessage(TextReplacer.create()
            .replace("msg", Utils.parseUrls(event.getMessage().getContentDisplay(), integration.getConfig()))
            .replace("user", event.getMember().getEffectiveName()).replace("userRepliedTo", replyUser)
            .replace("attachments", attachmentInfo).apply(baseText));
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        sendMessageToDiscord(
            integration.getConfig().messages.playerJoinMessage.replace("%user%", player.getName().getString()), null);
        updateRichPresence(1);
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (stopped) {
            return;
        }

        sendMessageToDiscord(
            integration.getConfig().messages.playerLeaveMessage.replace("%user%", player.getName().getString()), null);
        updateRichPresence(-1);
    }

    private void updateRichPresence(int modifier) {
        if (!integration.getConfig().showPlayerCountStatus) {
            return;
        }
        long playerCount = integration.getServer().getPlayerManager().getPlayerList().stream()
            .filter(p -> !integration.getVanishIntegration().isVanished(p)).count() + modifier;
        integration.getJda().getPresence().setPresence(Activity.playing(switch ((int) playerCount) {
            case 0 -> integration.getConfig().messages.onlineCountZero;
            case 1 -> integration.getConfig().messages.onlineCountSingular;
            default -> integration.getConfig().messages.onlineCountPlural.formatted(playerCount);
        }), false);
    }

    public void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        if (integration.getConfig().broadCastDeathMessages) {
            String message = source.getDeathMessage(player).getString();
            sendMessageToDiscord(message, null);
        }
    }

    public void onReceiveAdvancement(ServerPlayerEntity player, AdvancementDisplay advancement) {
        if (integration.getConfig().announceAdvancements && advancement.shouldAnnounceToChat()) {
            sendMessageToDiscord(
                integration.getConfig().messages.advancementMessage.replace("%user%", player.getName().getString())
                    .replace("%title%", advancement.getTitle().getString())
                    .replace("%description%", advancement.getDescription().getString()), null);
        }
    }

    public void sendMcChatMessage(Text message) {
        integration.getServer().getPlayerManager().broadcast(message, false);
    }

    private void onServerStopping(MinecraftServer minecraftServer) {
        sendMessageToDiscord(integration.getConfig().messages.stopMessage, null);
        stopped = true;
    }


    private void onMcChatMessage(SignedMessage signedMessage, ServerPlayerEntity player,
        MessageType.Parameters parameters) {
        String message = signedMessage.getContent().getString();
        sendMessageToDiscord(message, player);
    }

    private void sendMessageToDiscord(String message, ServerPlayerEntity sender) {
        if (this.messageSender == null || this.messageSender.hasChanged(message, sender)) {
            this.messageSender = new DiscordChatMessageSender(this.webhookClient, this.channel,
                this.integration.getConfig(), message, sender);
        }
        this.messageSender.sendMessage();
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        if (this.messageSender != null) {
            this.messageSender.onMessageDelete(event.getMessageIdLong());
        }
    }

    public void onCommandExecute(ServerCommandSource source, String command) {
        if (!command.startsWith("me") && !command.startsWith("say")) {
            return;
        }
        ServerPlayerEntity sender;
        String prefix;
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            sender = player;
            prefix = "";
        } else {
            sender = null;
            prefix = source.getName() + ": ";
        }
        String data = command.split(" ", 2)[1];
        String message;
        if (command.startsWith("me")) {
            message = prefix + "*" + data + "*";
        } else {
            message = prefix + data;
        }
        sendMessageToDiscord(message, sender);
    }
}
