package tronka.ordinarydiscordintegration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import tronka.ordinarydiscordintegration.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class OrdinaryDiscordIntegration extends ListenerAdapter implements DedicatedServerModInitializer {
    public static final String ModId = "ordinarydiscordintegration";
    public static final Logger LOGGER = LogManager.getLogManager().getLogger(ModId);
    public static JDA jda;
    public GuildMessageChannel serverChatChannel;
    public static Guild guild;
    public static List<Role> requiredRolesToJoin;
    @Override
    public void onInitializeServer() {
        if (Config.INSTANCE.botToken == null || Config.INSTANCE.botToken.length() < 20) {
            throw new RuntimeException("Please enter a valid bot token in the odi config file");
        }
        ServerMessageEvents.CHAT_MESSAGE.register(this::onChatMessage);
        jda = JDABuilder.createLight(Config.INSTANCE.botToken, GatewayIntent.GUILD_MESSAGES,GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(this).build();
    }




    @Override
    public void onReady(ReadyEvent event) {
        serverChatChannel = jda.getChannelById(GuildMessageChannel.class, Config.INSTANCE.serverChatChannel);
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
        if (Config.INSTANCE.unlinkOnLeave) {
            // unlink players
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getChannel() != serverChatChannel) {return;}
        MinecraftServer server;
//        server.send

    }

    private void onChatMessage(SignedMessage signedMessage, ServerPlayerEntity serverPlayerEntity, MessageType.Parameters parameters) {
        serverChatChannel.sendMessage(signedMessage.getContent().toString()).queue();
    }


}
