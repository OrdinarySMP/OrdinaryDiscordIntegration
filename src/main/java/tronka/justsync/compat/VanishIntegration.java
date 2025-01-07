package tronka.justsync.compat;

import me.drex.vanish.api.VanishAPI;
import me.drex.vanish.api.VanishEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import tronka.justsync.JustSyncApplication;

public class VanishIntegration {

    private boolean loaded;

    public VanishIntegration(JustSyncApplication integration) {
        if (!integration.getConfig().integrations.enableVanishIntegration) {
            return;
        }
        loaded = FabricLoader.getInstance().isModLoaded("melius-vanish");
        if (!loaded) {
            return;
        }
        VanishEvents.VANISH_EVENT.register((player, isVanished) -> {
            if (isVanished) {
                integration.getChatBridge().onPlayerLeave(player);
            } else {
                integration.getChatBridge().onPlayerJoin(player);
            }
        });
    }

    public boolean isVanished(ServerPlayerEntity player) {
        return loaded && VanishAPI.isVanished(player);
    }
}
