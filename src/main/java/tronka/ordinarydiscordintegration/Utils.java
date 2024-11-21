package tronka.ordinarydiscordintegration;

import com.mojang.logging.LogUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

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
}
