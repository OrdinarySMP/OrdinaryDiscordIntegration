package tronka.justsync.chat;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.minecraft.server.network.ServerPlayerEntity;
import tronka.justsync.config.Config;

public class DiscordChatMessageSender {

    private final String message;
    private final ServerPlayerEntity sender;
    private final JDAWebhookClient webhookClient;
    private final TextChannel channel;
    private final Config config;
    private long messageId;
    private long lastMessageEdit;
    private int repetitionCount;
    private int lastUpdatedCount;
    private CompletableFuture<Void> readyFuture;

    public DiscordChatMessageSender(JDAWebhookClient webhookClient, TextChannel channel, Config config, String message,
        ServerPlayerEntity sender) {
        this.webhookClient = webhookClient;
        this.channel = channel;
        this.config = config;
        this.message = message;
        this.sender = sender;
        this.repetitionCount = 0;
    }

    public boolean hasChanged(String message, ServerPlayerEntity sender) {
        return this.sender != sender || !message.equals(this.message);
    }


    public void sendMessage() {
        this.repetitionCount += 1;
        if (this.config.stackMessages && this.repetitionCount >= 2 && this.canUpdateMessage()) {
            this.updateMessage(this.messageId);
            return;
        }

        this.sendMessageToDiscord();
        this.lastMessageEdit = System.currentTimeMillis();
        this.repetitionCount = 1;
        this.lastUpdatedCount = 1;
    }

    public void onMessageDelete(long messageId) {
        if (this.messageId == messageId) {
            this.messageId = 0;
            this.repetitionCount = 0;
        }
    }

    private boolean canUpdateMessage() {
        return this.readyFuture == null || !this.readyFuture.isDone() || (
            this.channel.getLatestMessageIdLong() == this.messageId
                && this.lastMessageEdit + this.config.stackMessagesTimeoutInSec * 1000L > System.currentTimeMillis());
    }

    private void updateMessage(long messageId) {
        this.messageId = messageId;
        if (this.readyFuture != null && !this.readyFuture.isDone()) {
            return;
        }
        if (this.lastUpdatedCount == this.repetitionCount) {
            return;
        }
        if (this.lastMessageEdit + 1000 > System.currentTimeMillis()) {
            if (this.readyFuture == null || this.readyFuture.isDone()) {
                this.readyFuture = CompletableFuture.runAsync(() -> this.updateMessage(messageId),
                    CompletableFuture.delayedExecutor(System.currentTimeMillis() - this.lastMessageEdit + 1,
                        TimeUnit.MILLISECONDS));
            }
            return;
        }

        if (this.channel.getLatestMessageIdLong() != this.messageId) {
            this.repetitionCount = 0;
            this.sendMessage();
            return;
        }

        this.lastUpdatedCount = this.repetitionCount;
        this.lastMessageEdit = System.currentTimeMillis();
        editDiscordMessage();
    }

    private String cleanedMessage() {
        return message.replace("@everyone", "@ everyone")
            .replace("@here", "@ here")
            .replaceAll("<@&\\d+>", "@role-ping");
    }

    private String getMessage() {
        if (this.sender != null) {
            return this.sender.getName().getLiteralString() + ": " + this.cleanedMessage();
        }
        return this.cleanedMessage();
    }

    private void sendMessageToDiscord() {
        if (this.sender != null && this.webhookClient != null) {
            this.sendAsWebhook();
            return;
        }
        this.readyFuture = this.channel.sendMessage(this.getMessage()).submit().thenApply(Message::getIdLong)
            .thenAccept(this::updateMessage).exceptionally(this::handleFailure);
    }

    private void editDiscordMessage() {
        String displayedCount = " (" + this.repetitionCount + ")";
        if (this.sender != null && this.webhookClient != null) {
            this.editAsWebhook(this.cleanedMessage() + displayedCount);
            return;
        }
        this.channel.editMessageById(this.messageId, getMessage() + displayedCount).submit()
            .exceptionally(this::handleFailure);
    }

    private String getAvatarUrl(ServerPlayerEntity player) {
        return this.config.avatarUrl.replace("%UUID%", player.getUuid().toString())
            .replace("%randomUUID%", UUID.randomUUID().toString());
    }

    private void sendAsWebhook() {
        String avatarUrl = getAvatarUrl(sender);
        WebhookMessage msg = new WebhookMessageBuilder().setUsername(sender.getName().getLiteralString())
            .setAvatarUrl(avatarUrl).setContent(this.cleanedMessage()).build();
        this.readyFuture = this.webhookClient.send(msg).thenApply(ReadonlyMessage::getId)
            .thenAccept(this::updateMessage).exceptionally(this::handleFailure);

    }

    private void editAsWebhook(String message) {
        this.webhookClient.edit(messageId, message).exceptionally(this::handleFailure);
    }

    private <T> T handleFailure(Throwable t) {
        this.repetitionCount = 0;
        return null;
    }
}
