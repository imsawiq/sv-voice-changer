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

    /** MicThreadMixin's sendAudio hook is optional, so it is counted rather than assumed. */
    @Override
    long micHookCalls() throws ReflectiveOperationException {
        return (long) Reflect.call(controller(), "micHookCalls");
    }

    /** Why the hook might not run: was it merged into MicThread, and is that thread reading a microphone. */
    @Override
    String describeMicHook() throws ReflectiveOperationException {
        StringBuilder text = new StringBuilder("merged:");
        for (Method method : Reflect.type("de.maxhenkel.voicechat.voice.client.MicThread").getDeclaredMethods()) {
            if (method.getName().contains("svvoicechanger") || method.getName().contains("routeThroughAiVoice")) {
                text.append(' ').append(method.getName());
            }
        }
        Object client = Reflect.call(Reflect.type("de.maxhenkel.voicechat.voice.client.ClientManager"), "getClient");
        Object micThread = client == null ? null : Reflect.call(client, "getMicThread");
        text.append(" | micThread=").append(micThread);
        if (micThread != null) {
            text.append(" running=").append(Reflect.field(micThread, "running"))
                    .append(" locked=").append(Reflect.field(micThread, "microphoneLocked"));
            Object states = Reflect.call(Reflect.type("de.maxhenkel.voicechat.voice.client.ClientManager"),
                    "getPlayerStateManager");
            text.append(" disabled=").append(Reflect.call(states, "isDisabled"))
                    .append(" muted=").append(Reflect.call(states, "isMuted"));
            Object mic = Reflect.field(micThread, "mic");
            text.append(" mic=").append(mic);
            if (mic != null) {
                text.append(" started=").append(Reflect.call(mic, "isStarted"));
            }
        }
        return text.toString();
    }

    @Override
    String engineKindPackage() {
        return "svvoicechanger.client.ai.engine.";
    }

    /**
     * Voice activation at the lowest threshold, with voice activity detection
     * off where the installed version has it: detection would hold back room
     * noise, which is all there is to send while nobody speaks.
     */
    @Override
    Runnable sendOnRoomNoise() throws ReflectiveOperationException {
        Object config = Reflect.staticField("de.maxhenkel.voicechat.VoicechatClient", "CLIENT_CONFIG");
        List<Object[]> previous = new ArrayList<>();

        Object type = Reflect.field(config, "microphoneActivationType");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object voice = Enum.valueOf((Class) Reflect.type("de.maxhenkel.voicechat.voice.client.MicrophoneActivationType"), "VOICE");
        previous.add(new Object[]{type, Reflect.call(type, "get")});
        Reflect.call(type, "set", voice);

        Object threshold = Reflect.field(config, "voiceActivationThreshold");
        previous.add(new Object[]{threshold, Reflect.call(threshold, "get")});
        Reflect.call(threshold, "set", -127.0D);

        // A fresh client starts with voice chat off and muted until the player
        // finishes Simple Voice Chat's first-run setup, and then never reads
        // the microphone at all. The setup exists from 2.6 on.
        try {
            Object onboarding = Reflect.field(config, "onboardingFinished");
            previous.add(new Object[]{onboarding, Reflect.call(onboarding, "get")});
            Reflect.call(onboarding, "set", true);
        } catch (NoSuchFieldException ignored) {
            // No first-run setup on this version.
        }
        Object states = Reflect.call(Reflect.type("de.maxhenkel.voicechat.voice.client.ClientManager"),
                "getPlayerStateManager");
        boolean wasDisabled = (boolean) Reflect.call(states, "isDisabled");
        boolean wasMuted = (boolean) Reflect.call(states, "isMuted");
        Reflect.call(states, "setDisabled", false);
        Reflect.call(states, "setMuted", false);

        try {
            Object detection = Reflect.field(config, "vad");
            previous.add(new Object[]{detection, Reflect.call(detection, "get")});
            Reflect.call(detection, "set", false);
        } catch (NoSuchFieldException ignored) {
            // Versions before voice activity detection send noise anyway.
        }

        return () -> {
            try {
                for (Object[] entry : previous) {
                    Reflect.call(entry[0], "set", entry[1]);
                }
                Reflect.call(states, "setDisabled", wasDisabled);
                Reflect.call(states, "setMuted", wasMuted);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        };
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
