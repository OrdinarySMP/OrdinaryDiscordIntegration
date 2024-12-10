package tronka.ordinarydiscordintegration;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class InGameUnlinkCommand {

    private final OrdinaryDiscordIntegration integration;

    public InGameUnlinkCommand(OrdinaryDiscordIntegration integration) {
        this.integration = integration;
        CommandRegistrationCallback.EVENT.register(this::register);
    }

    private void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("unlink").executes(context -> {
            var player = context.getSource().getPlayer();
            if (player != null) {
                integration.getLinkManager().unlinkPlayer(player.getUuid());
                context.getSource().sendFeedback(() -> Text.literal("Unlinked!"), false);
                if (integration.getConfig().joining.enableLinking) {
                    player.networkHandler.disconnect(Text.literal(integration.getConfig().kickMessages.kickUnlinked));
                }
            } else {
                context.getSource().sendFeedback(() -> Text.literal("Player Only!"), false);
            }
            return 1;
        }).then(CommandManager.argument("player", GameProfileArgumentType.gameProfile()).requires(source -> source.hasPermissionLevel(2)).executes(context -> {
            var profiles = GameProfileArgumentType.getProfileArgument(context, "player");
            var kickedCount = 0;
            for (var profile : profiles) {
                integration.getLinkManager().unlinkPlayer(profile.getId());
                var player = context.getSource().getServer().getPlayerManager().getPlayer(profile.getId());
                if (player != null) {
                    if (integration.getConfig().joining.enableLinking) {
                        player.networkHandler.disconnect(Text.literal(integration.getConfig().kickMessages.kickUnlinked));
                    }
                    kickedCount++;
                }
            }
            int finalKickedCount = kickedCount;
            context.getSource().sendFeedback(() -> Text.literal("Successfully unlinked %d player and kicked %d".formatted(profiles.size(), finalKickedCount)), false);
            return 1;
        })));
    }
}
