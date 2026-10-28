package probe;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;

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

    /** The AI voice tab, which lives next to the studio. */
    Screen newAiScreen(Screen parent) throws ReflectiveOperationException {
        Class<?> screen = Reflect.type(aiScreenClass());
        return (Screen) screen.getConstructor(Screen.class, Reflect.type(this.controllerClass))
                .newInstance(parent, controller());
    }

    boolean isAiScreen(Screen screen) {
        return screen != null && screen.getClass().getName().equals(aiScreenClass());
    }

    private String aiScreenClass() {
        return this.studioClass.substring(0, this.studioClass.lastIndexOf('.') + 1) + "AiVoiceScreen";
    }

    /** The training wizard the AI voice tab opens for "Your own voice". */
    Screen newTrainScreen(Screen parent) throws ReflectiveOperationException {
        Class<?> screen = Reflect.type(trainScreenClass());
        return (Screen) screen.getConstructor(Screen.class, Reflect.type(this.controllerClass))
                .newInstance(parent, controller());
    }

    boolean isTrainScreen(Screen screen) {
        return screen != null && screen.getClass().getName().equals(trainScreenClass());
    }

    /** Moves the wizard to a step by name, as its Next and Back buttons do. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void trainGoTo(Screen wizard, String step) throws ReflectiveOperationException {
        Class stepType = Reflect.type(trainScreenClass() + "$Step");
        Method goTo = wizard.getClass().getDeclaredMethod("goTo", stepType);
        goTo.setAccessible(true);
        goTo.invoke(wizard, Enum.valueOf(stepType, step));
    }

    /** Switches the AI voice tab between its simple and advanced views. */
    void aiSwitchMode(Screen aiScreen, boolean advanced) throws ReflectiveOperationException {
        Method switchMode = aiScreen.getClass().getDeclaredMethod("switchMode", boolean.class);
        switchMode.setAccessible(true);
        switchMode.invoke(aiScreen, advanced);
    }

    /** Presses the wizard's main button, bottom right. */
    void trainPrimary(Screen wizard) throws ReflectiveOperationException {
        Method primary = wizard.getClass().getDeclaredMethod("onPrimary");
        primary.setAccessible(true);
        primary.invoke(wizard);
    }

    private String trainScreenClass() {
        return this.studioClass.substring(0, this.studioClass.lastIndexOf('.') + 1) + "AiTrainScreen";
    }

    /** Points the AI voice at another engine address, such as check.py's fake engine on its own port. */
    void useEngineAddress(String address) throws ReflectiveOperationException {
        UnaryOperator<Object> change = settings -> {
            try {
                Object engine = Reflect.call(settings, "voiceEngine");
                return Reflect.call(settings, "withVoiceEngine", Reflect.call(engine, "withAddress", address));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        };
        Reflect.call(controller(), "updateAiSettings", change);
    }

    /**
     * How often the voice changer's microphone hook has run, or -1 when the
     * mod does not count it. A hook whose mixin is optional can fail to attach
     * without an error, and only a count that keeps rising shows it works.
     */
    long micHookCalls() throws ReflectiveOperationException {
        return -1L;
    }

    /** What the probe can find out about the microphone hook when it does not run. */
    String describeMicHook() throws ReflectiveOperationException {
        return "not counted";
    }

    /** Switches the voice source between the AI voice and the effects. */
    void useAiVoice(boolean on) throws ReflectiveOperationException {
        UnaryOperator<Object> change = settings -> {
            try {
                return Reflect.call(settings, "withUseAi", on);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        };
        Reflect.call(controller(), "updateAiSettings", change);
    }

    /** The engine the AI voice speaks through, or null while none does. */
    String runningAiEngine() throws ReflectiveOperationException {
        Object engine = Reflect.call(Reflect.call(controller(), "aiVoice"), "runningEngine");
        return engine == null ? null : engine.toString();
    }

    /** What the AI voice reports about every engine, for the log. */
    String describeAi() throws ReflectiveOperationException {
        Object ai = Reflect.call(controller(), "aiVoice");
        StringBuilder text = new StringBuilder("running=" + Reflect.call(ai, "runningEngine")
                + " activity=" + Reflect.call(ai, "activity")
                + " startFailure=" + Reflect.call(ai, "startFailure"));
        for (Object kind : Reflect.type("org.sawiq." + engineKindPackage() + "EngineKind").getEnumConstants()) {
            text.append(" | ").append(Reflect.call(ai, "status", kind));
        }
        return text.toString();
    }

    /** Where the AI engine classes live under org.sawiq, which differs between the two mods. */
    abstract String engineKindPackage();

    /**
     * Makes the microphone send on room noise alone, so the AI voice gets audio
     * to convert without anybody speaking. Returns what puts it back.
     */
    abstract Runnable sendOnRoomNoise() throws ReflectiveOperationException;

    abstract boolean isVoiceConnected() throws ReflectiveOperationException;

    /**
     * Everything wrong with the mod's key bindings: one missing, or a name or
     * heading with no translation, which shows the player a raw key such as
     * "key.category.mod.thing" and makes the binding look absent.
     */
    abstract List<String> bindingProblems(Minecraft mc) throws ReflectiveOperationException;

    static boolean isTranslated(String key) {
        return Language.getInstance().has(key);
    }

    /** Opens the voice mod's own menu and, from there, the studio. */
    abstract void addWorldSteps(Probe probe);
}
