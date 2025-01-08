package tronka.justsync.mixin;

import com.mojang.authlib.GameProfile;
import java.net.SocketAddress;
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
import tronka.justsync.JustSyncApplication;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    private void canJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        JustSyncApplication integration = JustSyncApplication.getInstance();
        if (integration.getLinkManager().canJoin(profile.getId())) {
            return;
        }

        if (!integration.isReady()) {
            cir.setReturnValue(Text.of("DiscordJS not ready, please try again in a few seconds."));
            return;
        }

        cir.setReturnValue(Text.of(integration.getLinkManager().getJoinError(profile)));
    }

    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData,
        CallbackInfo ci) {
        JustSyncApplication.getInstance().getLinkManager().onPlayerJoin(player);
        if (!JustSyncApplication.getInstance().getVanishIntegration().isVanished(player)) {
            JustSyncApplication.getInstance().getChatBridge().onPlayerJoin(player);
        }
    }

}
