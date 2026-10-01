package probe;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "runtime_check_probe", dist = Dist.CLIENT)
public final class NeoForgeProbe {
    public NeoForgeProbe() {
        Probe probe = new Probe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> probe.tick(Minecraft.getInstance()));
    }
}
