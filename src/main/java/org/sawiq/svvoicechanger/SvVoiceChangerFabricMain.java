package org.sawiq.svvoicechanger;

//? if fabric {
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.sawiq.svvoicechanger.network.VoiceChangerNetwork;
import org.sawiq.svvoicechanger.server.VoiceChangerCommand;
import org.sawiq.svvoicechanger.server.VoiceChangerServerRuntime;

/**
 * The common entrypoint: it runs on a dedicated server and on the integrated
 * one, so a world opened to LAN gets the same policy as a real server.
 *
 * <p>The packet type is declared here rather than on the client, because a
 * payload registered on one side only is one neither side can use.</p>
 */
public final class SvVoiceChangerFabricMain implements ModInitializer {
    @Override
    public void onInitialize() {
        VoiceChangerNetwork.initialize();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> VoiceChangerCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                VoiceChangerServerRuntime.INSTANCE.start(server, FabricLoader.getInstance().getConfigDir()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                VoiceChangerServerRuntime.INSTANCE.stop());
    }
}
//?}
