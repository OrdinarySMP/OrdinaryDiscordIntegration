package tronka.ordinarydiscordintegration.mixin;

import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;
import tronka.ordinarydiscordintegration.config.Config;

@Mixin(ServerPlayNetworkHandler.class)
public class NetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void onPlayerLeave(DisconnectionInfo info, CallbackInfo ci) {
        OrdinaryDiscordIntegration.serverChatChannel.sendMessage(
                Config.INSTANCE.messages.playerLeaveMessage
                        .replace("%user%", player.getName().getString())
        ).queue();
    }
}
