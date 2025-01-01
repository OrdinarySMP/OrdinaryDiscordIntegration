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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
    private final List<Consumer<Config>> configReloadHandlers = new ArrayList<>();
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
        new InGameDiscordCommand(this);
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
        String reloadResult = tryReloadConfig();
        if (!reloadResult.isEmpty()) {
            throw new RuntimeException(reloadResult);
        }

        jda.addEventListener(new DiscordCommandHandler(this));
        jda.addEventListener(chatBridge = new ChatBridge(this));
        jda.addEventListener(consoleBridge = new ConsoleBridge(this));
        jda.addEventListener(linkManager = new LinkManager(this));
        luckPermsIntegration = new LuckPermsIntegration(this);
        vanishIntegration = new VanishIntegration(this);
        registerConfigReloadHandler(this::onConfigReloaded);
    }

    private void onConfigReloaded(Config config) {
        // bring all members into cache
        guild.loadMembers().onSuccess(members -> {
            setReady();
            linkManager.unlinkPlayers(members);
        }).onError(t -> {
            LOGGER.error("Unable to load members", t);
            setReady();
        });
    }

    private void setReady() {
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

    public String tryReloadConfig() {
        LOGGER.info("Reloading Config...");
        Config newConfig = Config.loadConfig();
        TextChannel serverChatChannel = Utils.getTextChannel(jda, newConfig.serverChatChannel);
        if (serverChatChannel == null) {
            return "Fail to load config: Please enter a valid serverChatChannelId";
        }


        guild = serverChatChannel.getGuild();
        config = newConfig;

        for (Consumer<Config> handler : configReloadHandlers) {
            handler.accept(config);
        }
        LOGGER.info("Config successfully reloaded!");
        return "";
    }

    public void registerConfigReloadHandler(Consumer<Config> handler) {
        configReloadHandlers.add(handler);
        handler.accept(config);
    }
}
