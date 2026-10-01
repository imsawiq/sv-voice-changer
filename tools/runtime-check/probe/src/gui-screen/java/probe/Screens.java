package probe;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Screen access from Minecraft 26.2, where the screen moved onto the GUI. */
final class Screens {
    private Screens() {
    }

    static Screen current(Minecraft mc) {
        return mc.gui.screen();
    }

    static void show(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    static boolean isLoading(Minecraft mc) {
        return mc.gui.overlay() != null;
    }

    static void screenshot(Minecraft mc, Consumer<Component> done) {
        Screenshot.grab(mc.gameDirectory, mc.gameRenderer.mainRenderTarget(), done);
    }
}
