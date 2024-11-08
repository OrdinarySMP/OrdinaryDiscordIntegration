package tronka.ordinarydiscordintegration;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class OrdinaryDiscordIntegration extends ListenerAdapter implements DedicatedServerModInitializer {
    public static final String ModId = "ordinarydiscordintegration";
    public static final Logger LOGGER = LogManager.getLogManager().getLogger(ModId);
    public static JDA jda;
    public TextChannel serverChatChannel;
    public static Guild guild;
    public static List<Role> requiredRolesToJoin;
    public static MinecraftServer server;
    private static Thread jdaThread;
    private static final String webHookId = "odi-bridge-hook";
    private static Webhook chatBridgeWebhook;

    @Override
    public void onInitializeServer() {
        if (Config.INSTANCE.botToken == null || Config.INSTANCE.botToken.length() < 20) {
            throw new RuntimeException("Please enter a valid bot token in the odi config file");
        }
        ServerMessageEvents.CHAT_MESSAGE.register(this::onChatMessage);
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        jdaThread = new Thread(this::startJDA);
        jdaThread.start();
    }


    private void startJDA() {
        jda = JDABuilder.createLight(Config.INSTANCE.botToken, GatewayIntent.GUILD_MESSAGES,GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(this).build();
    }

    private void onServerStopping(MinecraftServer server) {
        jda.shutdownNow();
    }



    @Override
    public void onReady(@NotNull ReadyEvent event) {
        serverChatChannel = jda.getChannelById(TextChannel.class, Config.INSTANCE.serverChatChannel);
        if (serverChatChannel == null) {
            throw new RuntimeException("Please enter a valid serverChatChannelId");
        }
        guild = serverChatChannel.getGuild();
        requiredRolesToJoin = new ArrayList<>();
        for (var roleId : Config.INSTANCE.joining.requiredJoinRoles) {
            var role = guild.getRoleById(roleId);
            if (role == null) {
                var namedRole = guild.getRoles().stream().filter(r -> r.getName().equals(roleId)).findFirst();
                if (namedRole.isEmpty()) {
                    LOGGER.warning("Could not find role with id \"%s\"".formatted(roleId));
                    continue;
                }
                role = namedRole.get();
            }
            requiredRolesToJoin.add(role);
        }

        guild.updateCommands()
                .addCommands(
                        Commands.slash("link", "Link your minecraft with the code you got when joining")
                                .addOption(OptionType.STRING, "code", "Link code", true)
                                .addSubcommands(new SubcommandData("alt", "Link an alt account")
                                        .addOption(OptionType.STRING, "code", "Link code", true),
                                        new SubcommandData("unlink", "Unlink your account")
//                                                .addOption(OptionType.USER, "user", "user to unlink")
                                        ),
                        Commands.slash("list", "List the currently online players"),
                        Commands.slash("status", "Show server status")
                ).queue();
        if (Config.INSTANCE.useWebHooks) {
            serverChatChannel.retrieveWebhooks().onSuccess((webhooks -> {
                var hook = webhooks.stream().filter(w -> w.getOwner() == guild.getSelfMember()).findFirst();
                if (hook.isPresent()) {
                    OrdinaryDiscordIntegration.chatBridgeWebhook = hook.get();
                } else {
                    serverChatChannel.createWebhook(webHookId).onSuccess(w -> chatBridgeWebhook = w).queue();
                }
            })).queue();
        }


        if (Config.INSTANCE.unlinkOnLeave) {
            // unlink players
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel() != serverChatChannel) {
            return;
        }
        if (event.getAuthor().isBot()) {
            return;
        }
        sendChatMessage(Text.of(event.getMessage().getContentStripped()));
    }

    public void sendChatMessage(Text message) {
        for (var player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(message);
        }
    }

    private void onChatMessage(SignedMessage signedMessage, ServerPlayerEntity serverPlayerEntity, MessageType.Parameters parameters) {
        if (chatBridgeWebhook != null) {
            sendAsWebhook(signedMessage.getContent().getLiteralString(), serverPlayerEntity);
        } else {
            serverChatChannel.sendMessage(Objects.requireNonNull(signedMessage.getContent().getLiteralString())).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "link":

                var code = event.getOptions().getFirst().getAsString();
                var message = switch(LinkManager.confirmLink(event.getUser().getIdLong(), code)) {
                    case LINKED -> "Successfully linked your account";
                    case FAILED_UNKNOWN -> "Unknown code, did you copy correctly?";
                    case FAILED_ALREADY_LINKED -> "You already have a Minecraft Account linked";
                };
                event.reply(message).setEphemeral(true).queue();

                break;
            case "alt":
                LOGGER.info("alt");
                break;
            case "list":
                break;
            case "status":
                break;
        }
    }

    private static String getAvatarUrl(ServerPlayerEntity player) {
        return Config.INSTANCE.avatarUrl.replace("%UUID%", player.getUuid().toString()).replace("%randomUUID%", UUID.randomUUID().toString());
    }

    public static void sendAsWebhook(String message, ServerPlayerEntity player) {
        try(var client = JDAWebhookClient.from(chatBridgeWebhook)) {
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
