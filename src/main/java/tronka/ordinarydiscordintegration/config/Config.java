package tronka.ordinarydiscordintegration.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import net.fabricmc.loader.api.FabricLoader;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public String botToken = "";
    public String serverChatChannel = "";
    public boolean useWebHooks = true;

    public String avatarUrl = "https://minotar.net/avatar/%UUID%?randomuuid=%randomUUID%";

    public boolean unlinkOnLeave = true;
    public int maxAlts = 1;

    public JoinOptions joining = new JoinOptions();

    public boolean stackMessages = false;

    public boolean broadCastDeathMessages = true;
    public boolean announceAdvancements = true;

    public boolean showPlayerCountStatus = true;

    public static class JoinOptions {
        public boolean enableLinking = true;
        public long linkCodeExpireMinutes = 10;
        public List<String> requiredRoles = new ArrayList<>();
        public List<String> joinRoles = new ArrayList<>();
        public boolean disallowTimeoutMembersToJoin = true;
        public boolean renameOnJoin = true;
    }

    public ErrorStrings kickMessages = new ErrorStrings();

    public static class ErrorStrings {
        public String kickMissingRoles = "You currently don't have the permission to join the server.";
        public String kickLinkCode = "Please Link your discord account by using\n/link %s\non discord";
        public String kickUnlinked = "Your account has been unlinked, to rejoin the server please relink your account.\nIf you don't know why this happened, please ask an administrator";
    }

    public MessageStrings messages = new MessageStrings();

    public static class MessageStrings {
        public String chatMessageFormat = "[§9Discord§r] <%user%> %msg%";
        public String commandExecutedInfoText = "%user% executed ``%msg%``";
        public String playerJoinMessage = "%user% joined";
        public String playerLeaveMessage = "%user% left";
        public String advancementMessage = "%user% just made the advancement **%title%**\n*%description%*";
        public String startMessage = "Server started";
        public String stopMessage = "Server stopped";
        public String onlineCount = "%d players online";
    }

    public DiscordLinkResults linkResults = new DiscordLinkResults();

    public static class DiscordLinkResults {
        public String linkSuccess = "Successfully linked to %name%";
        public String failedUnknownCode = "Unknown code, did you copy it correctly?";
        public String failedTooManyLinked = "You cannot link to another account";
    }

    public ExternalIntegrations integrations = new ExternalIntegrations();

    public static class ExternalIntegrations {
        public boolean enableVanishIntegration = true;
        public boolean enableLuckPermsIntegration = true;
        public LuckPermsIntegration luckPerms = new LuckPermsIntegration();

        public static class LuckPermsIntegration {
            public List<String> altGroups = new ArrayList<>();
        }
    }

    public CommandSettings commands = new CommandSettings();

    public static class CommandSettings {
        public String consoleChannel = "";
        public boolean showCommandsInConsole = true;
        public List<String> ignoredCommands = new ArrayList<>();
        public String commandPrefix = "//";
        public String opRole = "";

        public List<BridgeCommand> commands = List.of(new BridgeCommand[] {
                BridgeCommand.of("kick", "kick %args%"),
                BridgeCommand.of("stop", "stop"),
                BridgeCommand.of("kill", "kill %args%"),
                BridgeCommand.of("ban", "ban %args%")
        });
    }



    public static class BridgeCommand {
        public String commandName = "";
        public String inGameAction = "";
        public static BridgeCommand of(String name, String action) {
            var obj = new BridgeCommand();
            obj.commandName = name;
            obj.inGameAction = action;
            return obj;
        }
    }

    public static Config loadConfig() {
        var configDir = FabricLoader.getInstance().getConfigDir();
        var configFile = configDir.resolve(OrdinaryDiscordIntegration.ModId + ".toml").toFile();
        if (configFile.exists()){
            return new Toml().read(configFile).to(Config.class);
        } else {
            var config = new Config();
            try {
                new TomlWriter().write(config, configFile);
            } catch (IOException ignored) { }
            return config;
        }

    }

}
