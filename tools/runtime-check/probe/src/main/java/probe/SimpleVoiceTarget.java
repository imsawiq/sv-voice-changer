package probe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Simple Voice Voice Changer, opened the way players reach it: the button on the voice chat menu. */
final class SimpleVoiceTarget extends Target {
    /** Where VoiceChatScreenMixin puts its button, relative to the menu's top-left corner. */
    private static final int BUTTON_X = (195 - 20) / 2;
    private static final int BUTTON_Y = 21;
    private static final List<String> KEY_MAPPINGS = List.of("key.svvoicechanger.toggle", "key.svvoicechanger.open_studio");

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

    /** Minecraft's own key binds: each one present, named, and under a heading that is not a raw key. */
    @Override
    List<String> bindingProblems(Minecraft mc) throws ReflectiveOperationException {
        List<String> problems = new ArrayList<>();
        for (String name : KEY_MAPPINGS) {
            KeyMapping mapping = null;
            for (KeyMapping candidate : mc.options.keyMappings) {
                if (candidate.getName().equals(name)) {
                    mapping = candidate;
                }
            }
            if (mapping == null) {
                problems.add("key bind " + name + " is not registered");
                continue;
            }
            if (!isTranslated(name)) {
                problems.add("key bind " + name + " has no translated name");
            }
            String heading = headingKey(mapping.getCategory());
            String headingProblem = "key bind heading " + heading + " has no translation";
            if (heading != null && !isTranslated(heading) && !problems.contains(headingProblem)) {
                problems.add(headingProblem);
            }
        }
        return problems;
    }

    /**
     * Up to 1.21.8 the category is the translation key itself; from 1.21.9 it
     * is an object whose label is a translatable component. The label method
     * is found by its shape, since Fabric renames it outside the dev
     * environment.
     */
    private static String headingKey(Object category) throws ReflectiveOperationException {
        if (category instanceof String key) {
            return key;
        }
        for (Method method : category.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && Component.class.isAssignableFrom(method.getReturnType())) {
                Component label = (Component) method.invoke(category);
                return label.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
            }
        }
        return null;
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
