package tronka.ordinarydiscordintegration;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Utils {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static List<Role> parseRoleList(Guild guild, List<String> roleIds) {
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

    public static TextChannel getTextChannel(JDA jda, String id) {
        if (id == null || id.length() < 5) { return null; }
        return jda.getTextChannelById(id);
    }

    public static String getPlayerName(UUID uuid) {
        var result = OrdinaryDiscordIntegration.getInstance().getServer().getSessionService().fetchProfile(uuid, false);
        if (result == null) {
            return "unknown";
        }
        return result.profile().getName();
    }

    private static Gson gson = new Gson();

    public static GameProfile fetchProfile(String name) {
        try {
            var url = URI.create("https://api.mojang.com/users/profiles/minecraft/" + name).toURL();
            var connection = url.openConnection();
            connection.addRequestProperty("User-Agent", "OrdinaryDiscordIntegration");
            connection.addRequestProperty("Accept", "application/json");
            connection.connect();
            var reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            var data = reader.lines().collect(Collectors.joining());
            // fix uuid format
            var fixed = data.replaceFirst("\"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)\"", "$1-$2-$3-$4-$5");
            return gson.fromJson(fixed, GameProfile.class);
        } catch (IOException e) {

            return null;
        }
    }
}
