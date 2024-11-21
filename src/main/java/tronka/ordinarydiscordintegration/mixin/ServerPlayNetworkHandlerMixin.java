package tronka.ordinarydiscordintegration.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "executeCommand", at = @At("HEAD"))
    private void onExecuteCommand(String command, CallbackInfo ci) {
        OrdinaryDiscordIntegration.getInstance().getConsoleBridge().onCommandExecute(command, player);
    }
}
