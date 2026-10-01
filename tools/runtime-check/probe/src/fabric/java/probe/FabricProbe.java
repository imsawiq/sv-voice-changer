package probe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricProbe implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Probe probe = new Probe();
        ClientTickEvents.END_CLIENT_TICK.register(probe::tick);
    }
}
