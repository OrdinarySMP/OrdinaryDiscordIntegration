package tronka.ordinarydiscordintegration.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tronka.ordinarydiscordintegration.OrdinaryDiscordIntegration;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci){
        OrdinaryDiscordIntegration.getInstance().getChatBridge().onPlayerDeath((ServerPlayerEntity)(Object)this, damageSource);
    }
}
