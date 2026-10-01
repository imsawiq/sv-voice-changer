package probe;

import java.lang.reflect.Method;
import net.minecraft.client.gui.screens.Screen;

/** One voice changer build: where its studio and controller live, and how to reach it in a world. */
abstract class Target {
    private final String studioClass;
    private final String controllerClass;

    Target(String studioClass, String controllerClass) {
        this.studioClass = studioClass;
        this.controllerClass = controllerClass;
    }

    Object controller() throws ReflectiveOperationException {
        return Reflect.staticField(this.controllerClass, "INSTANCE");
    }

    boolean isInitialized() throws ReflectiveOperationException {
        return (boolean) Reflect.call(controller(), "isInitialized");
    }

    void setEnabled(boolean enabled) throws ReflectiveOperationException {
        Reflect.call(controller(), "setEnabled", enabled);
    }

    void setSelfListen(boolean enabled) throws ReflectiveOperationException {
        Reflect.call(controller(), "setSelfListenEnabled", enabled);
    }

    /** Whether microphone blocks are going through the effect chain right now. */
    boolean isAudioFlowing() throws ReflectiveOperationException {
        Object diagnostics = Reflect.call(controller(), "getDiagnostics");
        return (boolean) Reflect.call(diagnostics, "isRunning") && (int) Reflect.call(diagnostics, "channels") > 0;
    }

    /** What the audio chain reports, for the log: whether it runs, and what it sees. */
    String describeAudio() throws ReflectiveOperationException {
        return "effectActive=" + Reflect.call(controller(), "isEffectActive")
                + " " + Reflect.call(controller(), "getDiagnostics");
    }

    Screen newStudio(Screen parent) throws ReflectiveOperationException {
        Class<?> studio = Reflect.type(this.studioClass);
        return (Screen) studio.getConstructor(Screen.class, Reflect.type(this.controllerClass))
                .newInstance(parent, controller());
    }

    boolean isStudio(Screen screen) {
        return screen != null && screen.getClass().getName().equals(this.studioClass);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void switchToAdvanced(Screen studio) throws ReflectiveOperationException {
        if (!isStudio(studio)) {
            throw new IllegalStateException("Not on the studio: " + Probe.describe(studio));
        }
        Class mode = Reflect.type(this.studioClass + "$Mode");
        Method switchMode = studio.getClass().getDeclaredMethod("switchMode", mode);
        switchMode.setAccessible(true);
        switchMode.invoke(studio, Enum.valueOf(mode, "ADVANCED"));
    }

    abstract boolean isVoiceConnected() throws ReflectiveOperationException;

    /** Opens the voice mod's own menu and, from there, the studio. */
    abstract void addWorldSteps(Probe probe);
}
