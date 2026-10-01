package probe;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** Simple Voice Voice Changer, opened the way players reach it: the button on the voice chat menu. */
final class SimpleVoiceTarget extends Target {
    /** Where VoiceChatScreenMixin puts its button, relative to the menu's top-left corner. */
    private static final int BUTTON_X = (195 - 20) / 2;
    private static final int BUTTON_Y = 21;

    SimpleVoiceTarget() {
        super("org.sawiq.svvoicechanger.client.ui.VoiceChangerStudioScreen",
                "org.sawiq.svvoicechanger.client.VoiceChangerController");
    }

    @Override
    boolean isVoiceConnected() throws ReflectiveOperationException {
        Object client = Reflect.call(Reflect.type("de.maxhenkel.voicechat.voice.client.ClientManager"), "getClient");
        if (client == null) {
            return false;
        }
        Object connection = Reflect.call(client, "getConnection");
        return connection != null && (boolean) Reflect.call(connection, "isInitialized");
    }

    @Override
    void addWorldSteps(Probe probe) {
        probe.step("Simple Voice Chat menu", 200, (mc, t) -> {
            if (t == 0) {
                Screens.show(mc, (Screen) Reflect.type("de.maxhenkel.voicechat.gui.VoiceChatScreen")
                        .getConstructor().newInstance());
            }
            return t >= 40;
        });
        probe.screenshot("svc-menu");
        probe.step("studio from the voice changer button", 200, (mc, t) -> {
            if (t == 0) {
                pressStudioButton(Screens.current(mc));
            }
            return t >= 40 && isStudio(Screens.current(mc));
        });
        probe.screenshot("world-studio");
    }

    private static void pressStudioButton(Screen menu) throws ReflectiveOperationException {
        int left = (int) Reflect.call(menu, "getGuiLeft");
        int top = (int) Reflect.call(menu, "getGuiTop");
        for (GuiEventListener child : menu.children()) {
            if (child instanceof AbstractWidget widget
                    && child.getClass().getName().equals("de.maxhenkel.voicechat.gui.widgets.ImageButton")
                    && widget.getX() == left + BUTTON_X && widget.getY() == top + BUTTON_Y) {
                Object action = Reflect.field(child, "onPress");
                Reflect.call(action, "onPress", child);
                return;
            }
        }
        throw new IllegalStateException("The voice chat menu has no voice changer button");
    }
}
