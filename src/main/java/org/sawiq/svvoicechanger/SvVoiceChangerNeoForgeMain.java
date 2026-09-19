package org.sawiq.svvoicechanger;

//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.sawiq.svvoicechanger.network.VoiceChangerNetwork;
import org.sawiq.svvoicechanger.server.ServerPermissions;
import org.sawiq.svvoicechanger.server.VoiceChangerCommand;
import org.sawiq.svvoicechanger.server.VoiceChangerServerRuntime;

/^*
 * The common entrypoint: it runs on a dedicated server and on the integrated
 * one, so a world opened to LAN gets the same policy as a real server.
 *
 * <p>Its own mod class rather than a branch inside the client one, which is how
 * NeoForge wants a mod split by physical side: nothing the client half touches
 * is ever loaded on a dedicated server.</p>
 ^/
@Mod(SvVoiceChanger.NEOFORGE_MOD_ID)
public final class SvVoiceChangerNeoForgeMain {
    public SvVoiceChangerNeoForgeMain(IEventBus modEventBus) {
        VoiceChangerNetwork.initialize();
        modEventBus.addListener(VoiceChangerNetwork::registerPayloads);

        NeoForge.EVENT_BUS.addListener(ServerPermissions::gather);
        NeoForge.EVENT_BUS.addListener(SvVoiceChangerNeoForgeMain::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SvVoiceChangerNeoForgeMain::onServerStarting);
        NeoForge.EVENT_BUS.addListener(SvVoiceChangerNeoForgeMain::onServerStopped);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        VoiceChangerCommand.register(event.getDispatcher());
    }

    private static void onServerStarting(ServerStartingEvent event) {
        VoiceChangerServerRuntime.INSTANCE.start(event.getServer(), FMLPaths.CONFIGDIR.get());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        VoiceChangerServerRuntime.INSTANCE.stop();
    }
}
*///?}
