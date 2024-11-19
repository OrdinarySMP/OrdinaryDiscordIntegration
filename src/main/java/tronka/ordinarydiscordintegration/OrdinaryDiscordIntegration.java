package tronka.ordinarydiscordintegration;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
import org.slf4j.Logger;
import tronka.ordinarydiscordintegration.config.Config;
import tronka.ordinarydiscordintegration.linking.LinkManager;
import tronka.ordinarydiscordintegration.linking.PlayerData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrdinaryDiscordIntegration extends ListenerAdapter implements DedicatedServerModInitializer {
    public static final String ModId = "ordinarydiscordintegration";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static JDA jda;
    public static TextChannel serverChatChannel;
    public static TextChannel consoleChannel;
    public static Guild guild;
    public static List<Role> requiredRolesToJoin;
    public static List<Role> joinAssignRoles;
    public static MinecraftServer server;
    private static Thread jdaThread;
    private static final String webHookId = "odi-bridge-hook";
    private static Webhook chatBridgeWebhook;
    public static boolean isReady = false;

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

    public static void onCommandExecute(String command, ServerPlayerEntity player) {
        if (consoleChannel == null) {
            return;
        }
        if (!Config.INSTANCE.showCommandsInConsole) {
            return;
        }
        consoleChannel.sendMessage(
                Config.INSTANCE.messages.commandExecutedInfoText
                        .replace("%user%", player.getName().getString())
                        .replace("%msg%", command)
        ).queue();
    }



    @Override
    public void onReady(@NotNull ReadyEvent event) {
        serverChatChannel = getTextChannel(Config.INSTANCE.serverChatChannel);
        consoleChannel = getTextChannel(Config.INSTANCE.consoleChannel);

        if (serverChatChannel == null) {
            throw new RuntimeException("Please enter a valid serverChatChannelId");
        }

        guild = serverChatChannel.getGuild();
        requiredRolesToJoin = parseRoleList(Config.INSTANCE.joining.requiredJoinRoles);
        joinAssignRoles = parseRoleList(Config.INSTANCE.joining.assignRoleAtJoin);


        guild.updateCommands()
                .addCommands(
                        Commands.slash("plink", "Link your minecraft with the code you got when joining")
                                .addOption(OptionType.STRING, "code", "Link code", true),
                        Commands.slash("linking", "Misc linking stuff")
                                .addSubcommands(new SubcommandData("get", "Retrieve linking information")
                                                .addOption(OptionType.USER, "user", "whose data to get"),
                                            new SubcommandData("unlink", "Unlink your account")
                                                .addOption(OptionType.USER, "user", "user to unlink")
                                        ),
                        Commands.slash("list", "List the currently online players")
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

        guild.loadMembers().onSuccess(members -> {
            isReady = true;
            if (Config.INSTANCE.unlinkOnLeave) {

                LinkManager.unlinkPlayers(members);
            }
        }).onError(t -> {
            LOGGER.error("Unable to load members", t);
            isReady = true;
        });
    }

    private static List<Role> parseRoleList(List<String> roleIds) {
        var roles = new ArrayList<Role>();
        if (roleIds == null) { return roles; }
        for (var roleId : roleIds) {
            var role = guild.getRoleById(roleId);
            if (role == null) {
                var namedRole = guild.getRoles().stream().filter(r -> r.getName().equals(roleId)).findFirst();
                if (namedRole.isEmpty()) {
                    LOGGER.warn("Could not find role with id \"{}\"", roleId);
                    continue;
                }
                role = namedRole.get();
            }
            roles.add(role);
        }
        return roles;
    }

    private static TextChannel getTextChannel(String id) {
        if (id == null || id.length() < 5) { return null; }
        return jda.getTextChannelById(id);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel() != serverChatChannel) {
            return;
        }
        if (event.getAuthor().isBot()) {
            return;
        }
        var message = Text.of(
                Config.INSTANCE.messages.chatMessageFormat
                        .replace("%user%", event.getAuthor().getName())
                        .replace("%msg%", event.getMessage().getContentDisplay())
        );
        sendChatMessage(message);
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
            var formattedMessage = serverPlayerEntity.getName() + ": " + signedMessage.getContent().getLiteralString();
            serverChatChannel.sendMessage(formattedMessage).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || event.getMember().getGuild() != guild) { return; }
        switch (event.getName()) {
            case "link" -> {
                var code = event.getOptions().getFirst().getAsString();
                var message = LinkManager.confirmLink(event.getUser().getIdLong(), code);
                event.reply(message).setEphemeral(true).queue();
            }
            case "list" -> {
                event.reply(String.join(", ", server.getPlayerNames())).setEphemeral(true).queue();
            }
            case "linking" -> {
                var user = event.getOption("user", OptionMapping::getAsUser);
                if (user == null) {
                    user = event.getUser();
                }
                var plink = LinkManager.getDataOf(user.getIdLong());
                switch (event.getSubcommandName()) {
                    case "get" -> {
                        String message;
                        if (plink.isPresent()) {
                            var link = plink.get();
                            message = user.getAsMention() + " is linked to " + link.getPlayerName();
                            var alts = link.getAlts();
                            if (!alts.isEmpty()) {
                                message += "\nWith " + alts.size() + " alts: " + String.join(", ", alts.stream().map(PlayerData::getName).toList());
                            }

                        } else {
                            message = user.getAsMention() + " is not linked to any player";
                        }
                        event.reply(message).setEphemeral(true).queue();
                    }
                    case "unlink" -> {
                        if (plink.isEmpty()) {
                            var message = user.equals(event.getUser()) ?
                                    "You are not linked to any player" :
                                    user.getAsMention() + " is not linked to any player";
                            event.reply(message).setEphemeral(true).queue();
                            return;
                        }
                        if (!user.equals(event.getUser()) && !event.getMember().getPermissions().contains(Permission.ADMINISTRATOR)) {
                            event.reply("You do not have permission to use this command!").setEphemeral(true).queue();
                            return;
                        }
                        LinkManager.unlinkPlayer(user.getIdLong());
                        event.reply("Successfully unlinked " + user.getAsMention()).setEphemeral(true).queue();
                    }
                    case null -> {}
                    default -> throw new IllegalStateException("Unexpected value: " + event.getSubcommandName());
                }
            }
        }
    }

    private static String getAvatarUrl(ServerPlayerEntity player) {
        return Config.INSTANCE.avatarUrl
                .replace("%UUID%", player.getUuid().toString())
                .replace("%randomUUID%", UUID.randomUUID().toString());
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
