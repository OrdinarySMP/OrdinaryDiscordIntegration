package tronka.ordinarydiscordintegration.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tronka.ordinarydiscordintegration.Linkmanager;

import java.net.SocketAddress;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    private void canJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        if (Linkmanager.canJoin(profile.getId())) { return; }

        var linkCode = Linkmanager.generateLinkCode(profile.getId());
        var kickText = Text.of("Blabla bla not linked use \n /link " + linkCode);
        cir.setReturnValue(kickText);
    }
}
