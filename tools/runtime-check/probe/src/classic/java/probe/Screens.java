package probe;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Screen access up to Minecraft 26.1, where the client itself holds the screen. */
final class Screens {
    private Screens() {
    }

    static Screen current(Minecraft mc) {
        return mc.screen;
    }

    static void show(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    static boolean isLoading(Minecraft mc) {
        return mc.getOverlay() != null;
    }

    static void screenshot(Minecraft mc, Consumer<Component> done) {
        Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), done);
    }
}
