package org.sawiq.svvoicechanger.client;

//? if neoforge {
/*import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.sawiq.svvoicechanger.SvVoiceChanger;

@Mod(value = SvVoiceChanger.NEOFORGE_MOD_ID, dist = Dist.CLIENT)
public final class SvVoiceChangerNeoForge {
    public SvVoiceChangerNeoForge(IEventBus modEventBus) {
        VoiceChangerClientRuntime runtime = VoiceChangerClientRuntime.INSTANCE;
        runtime.initialize(FMLPaths.CONFIGDIR.get());
        modEventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        VoiceChangerClientRuntime.INSTANCE.registerHotkeys(event::register);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        VoiceChangerClientRuntime.INSTANCE.tick(Minecraft.getInstance());
    }
}
*///?}
