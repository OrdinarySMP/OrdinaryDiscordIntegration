package tronka.ordinarydiscordintegration.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import net.fabricmc.loader.api.FabricLoader;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;

import java.io.IOException;
import java.util.List;

public class Config {
    public static Config INSTANCE = loadConfig();

    public String botToken;
    public String serverChatChannel;
    public String consoleChannel;

    public boolean unlinkOnLeave;

    public JoinOptions joining;

    public static class JoinOptions {
        public boolean enableLinking;
        public long linkCodeExpireMinutes = 10;
        public List<String> requiredJoinRoles;
    }

    public ErrorStrings strings = new ErrorStrings();

    public static class ErrorStrings {
        public String kickMissingRoles = "You currently don't have the permission to join the server.";
        public String kickLinkCode = "Please Link your discord account by using\n/link %s\non discord";
    }



    private static Config loadConfig(){
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
