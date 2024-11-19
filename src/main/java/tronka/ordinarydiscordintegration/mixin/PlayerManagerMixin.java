package tronka.ordinarydiscordintegration.mixin;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.Permission;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.config.Config;
import tronka.ordinarydiscordintegration.linking.LinkManager;

import java.net.SocketAddress;
import java.util.Collections;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    private void canJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        if (LinkManager.canJoin(profile.getId())) { return; }

        if (!OrdinaryDiscordIntegration.isReady) {
            cir.setReturnValue(Text.of("odi not ready, please try again in a few seconds."));
            return;
        }

        cir.setReturnValue(Text.of(LinkManager.getJoinError(profile)));
    }

    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci){
        OrdinaryDiscordIntegration.serverChatChannel.sendMessage(
                Config.INSTANCE.messages.playerJoinMessage
                        .replace("%user%", player.getName().getString())
        ).queue();
        var memberOptional = LinkManager.getDiscordOf(player.getUuid());
        if (memberOptional.isPresent()) {
            var member = memberOptional.get();
            if (OrdinaryDiscordIntegration.guild.getSelfMember().getPermissions().contains(Permission.NICKNAME_MANAGE)) {
                member.modifyNickname(player.getName().getString()).queue();
            }
            if (OrdinaryDiscordIntegration.guild.getSelfMember().getPermissions().contains(Permission.MANAGE_ROLES)){
                member.getGuild().modifyMemberRoles(member, OrdinaryDiscordIntegration.joinAssignRoles, Collections.emptyList()).queue();
            }
        }
    }
}
