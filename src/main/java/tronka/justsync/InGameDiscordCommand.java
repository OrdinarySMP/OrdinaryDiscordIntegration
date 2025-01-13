package tronka.justsync;

import java.util.Collection;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class InGameDiscordCommand {

    private final JustSyncApplication integration;

    public InGameDiscordCommand(JustSyncApplication integration) {
        this.integration = integration;
        CommandRegistrationCallback.EVENT.register(this::register);
    }

    private void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry,
        CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            CommandManager.literal("discord").then(CommandManager.literal("unlink").requires(Permissions.require("justsync.unlink", true)).executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    this.integration.getLinkManager().unlinkPlayer(player.getUuid());
                    context.getSource().sendFeedback(() -> Text.literal("Unlinked!"), false);
                } else {
                    context.getSource().sendFeedback(() -> Text.literal("Player Only!"), false);
                }
                return 1;
            }).then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                .requires(Permissions.require("justsync.unlink.other", 4)).executes(context -> {
                    Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");

                    for (GameProfile profile : profiles) {
                        this.integration.getLinkManager().unlinkPlayer(profile.getId());
                    }
                    context.getSource().sendFeedback(() -> Text.literal(
                            "Successfully unlinked %d player(s)".formatted(profiles.size())),
                        false);
                    return 1;
                }))).then(
                CommandManager.literal("reload").requires(Permissions.require("justsync.reload", 4)).executes(context -> {
                    String result = this.integration.tryReloadConfig();
                    final String feedback = result.isEmpty() ? "Successfully reloaded config!" : result;
                    context.getSource().sendFeedback(() -> Text.literal(feedback), result.isEmpty());
                    return 1;
                })));
    }
}
