package tronka.ordinarydiscordintegration;

import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import tronka.ordinarydiscordintegration.compat.LuckPermsIntegration;
import tronka.ordinarydiscordintegration.compat.VanishIntegration;
import tronka.ordinarydiscordintegration.config.Config;
import tronka.ordinarydiscordintegration.linking.LinkManager;

public class OrdinaryDiscordIntegration extends ListenerAdapter implements DedicatedServerModInitializer {
    public static final String ModId = "ordinarydiscordintegration";
    private static final Logger LOGGER = LogUtils.getLogger();
    private JDA jda;
    private Guild guild;
    private MinecraftServer server;
    private boolean ready = false;
    private ConsoleBridge consoleBridge;
    private ChatBridge chatBridge;
    private static OrdinaryDiscordIntegration instance;
    private Config config = Config.loadConfig();
    private LinkManager linkManager;
    private LuckPermsIntegration luckPermsIntegration;
    private VanishIntegration vanishIntegration;

    @Override
    public void onInitializeServer() {
        instance = this;
        if (config.botToken == null || config.botToken.length() < 20) {
            throw new RuntimeException("Please enter a valid bot token in the odi config file");
        }
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);
        Thread jdaThread = new Thread(this::startJDA);
        new InGameUnlinkCommand(this);
        jdaThread.start();
    }

    private void startJDA() {
        jda = JDABuilder.createLight(config.botToken, GatewayIntent.GUILD_MESSAGES,GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(this).build();
    }

    private void onServerStopped(MinecraftServer server) {
        jda.shutdownNow();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        TextChannel serverChatChannel = Utils.getTextChannel(jda, config.serverChatChannel);
        TextChannel consoleChannel = Utils.getTextChannel(jda, config.commands.consoleChannel);
        if (serverChatChannel == null) {
            throw new RuntimeException("Please enter a valid serverChatChannelId");
        }

        guild = serverChatChannel.getGuild();

        jda.addEventListener(new DiscordCommandHandler(this));
        jda.addEventListener(chatBridge = new ChatBridge(this, serverChatChannel));
        jda.addEventListener(consoleBridge = new ConsoleBridge(this, consoleChannel));
        jda.addEventListener(linkManager = new LinkManager(this));
        luckPermsIntegration = new LuckPermsIntegration(this);
        vanishIntegration = new VanishIntegration(this);

        guild.loadMembers().onSuccess(members -> {
           setReady();
           linkManager.unlinkPlayers(members);
        }).onError(t -> {
            LOGGER.error("Unable to load members", t);
            setReady();
        });
    }

    public void setReady() {
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }

    public JDA getJda() {
        return jda;
    }

    public static OrdinaryDiscordIntegration getInstance() {
        return instance;
    }

    public Config getConfig() {
        return config;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Guild getGuild() {
        return guild;
    }

    public ConsoleBridge getConsoleBridge() {
        return consoleBridge;
    }

    public ChatBridge getChatBridge() {
        return chatBridge;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public LuckPermsIntegration getLuckPermsIntegration() {
        return luckPermsIntegration;
    }

    public VanishIntegration getVanishIntegration() {
        return vanishIntegration;
    }

    public void reloadConfig() {
        config = Config.loadConfig();
    }
}
