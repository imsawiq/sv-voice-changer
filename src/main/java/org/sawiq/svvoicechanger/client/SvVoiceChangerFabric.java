package org.sawiq.svvoicechanger.client;

//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import org.sawiq.svvoicechanger.client.server.ClientVoiceChannel;

public final class SvVoiceChangerFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VoiceChangerClientRuntime runtime = VoiceChangerClientRuntime.INSTANCE;
        runtime.initialize(FabricLoader.getInstance().getConfigDir());
        ClientVoiceChannel.initialize(payload ->
                VoiceChangerController.INSTANCE.serverSession().receive(payload));
        runtime.registerHotkeys(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> runtime.shutdown());
    }
}
//?}
