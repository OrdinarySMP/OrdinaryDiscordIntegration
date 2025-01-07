package tronka.justsync.mixin;


import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tronka.justsync.JustSyncApplication;

@Mixin(CommandManager.class)
public class CommandManagerMixin {

    @Inject(method = "execute", at = @At("HEAD"))
    private void onExecuteCommand(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfo ci) {
        JustSyncApplication.getInstance().getConsoleBridge()
            .onCommandExecute(parseResults.getContext().getSource(), command);
        JustSyncApplication.getInstance().getChatBridge()
            .onCommandExecute(parseResults.getContext().getSource(), command);
    }
}
