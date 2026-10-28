package probe;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * check.py --gallery: the screens players see, set up the way they look in use,
 * for the mod pages. The voice mod's own menu comes from the target's world
 * steps; everything else is opened here, against the fake engine.
 */
final class Gallery {
    /** One of the voices check.py's fake engine lists in gallery mode. */
    private static final String SHOWN_VOICE = "rvc/deep_narrator";
    private static final String SHOWN_PRESET = "TITAN";

    private Gallery() {
    }

    static void addSteps(Probe probe, Target target) {
        target.addWorldSteps(probe);

        probe.step("gallery: studio with a preset", 200, (mc, t) -> {
            if (t == 0) {
                target.useAiVoice(false);
                target.setEnabled(true);
                applyPreset(target, SHOWN_PRESET);
                Screens.show(mc, target.newStudio(null));
            }
            return t >= 60 && target.isStudio(Screens.current(mc));
        });
        shot(probe, "gallery-studio");
        probe.step("gallery: studio, every setting", 100, (mc, t) -> {
            if (t == 0) {
                target.switchToAdvanced(Screens.current(mc));
            }
            return t >= 40;
        });
        shot(probe, "gallery-studio-advanced");

        Object[] savedProgress = new Object[1];
        probe.step("gallery: engine installing", 100, (mc, t) -> {
            if (t == 0) {
                // As on a first install: no engine answers yet. The installer
                // state comes first, so the installed engine is not started
                // on the dead port in between.
                savedProgress[0] = showInstalling(target);
                target.useEngineAddress("127.0.0.1:9");
            } else if (t == 40) {
                // Only once the engines have been asked again: switched on
                // earlier, the AI voice would try the address it last saw ready.
                target.useAiVoice(true);
                Screens.show(mc, target.newAiScreen(null));
            }
            return t >= 100;
        });
        shot(probe, "gallery-install");
        probe.step("gallery: install over", 40, (mc, t) -> {
            if (t == 0) {
                setInstallerProgress(target, savedProgress[0]);
                target.useEngineAddress(Files.readString(Path.of("probe-engine-address")).strip());
            }
            return t >= 10;
        });

        probe.step("gallery: AI voice speaking", 400, (mc, t) -> {
            if (t == 0) {
                useVoice(target, SHOWN_VOICE);
                target.useAiVoice(true);
                Screens.show(mc, target.newAiScreen(null));
            }
            return t >= 80 && "VOICE_ENGINE".equals(target.runningAiEngine());
        });
        shot(probe, "gallery-ai");
        probe.step("gallery: voice gallery", 100, (mc, t) -> {
            if (t == 0) {
                Object kind = engineKind(target, "VOICE_ENGINE");
                Reflect.call(Screens.current(mc), "openGallery", kind);
            }
            return t >= 40;
        });
        shot(probe, "gallery-voices");
        probe.step("gallery: AI voice, every engine", 100, (mc, t) -> {
            if (t == 0) {
                Screens.show(mc, target.newAiScreen(null));
            } else if (t == 20) {
                target.aiSwitchMode(Screens.current(mc), true);
            }
            return t >= 60;
        });
        shot(probe, "gallery-ai-advanced");

        probe.step("gallery: training, recordings", 200, (mc, t) -> {
            if (t == 0) {
                Screens.show(mc, target.newTrainScreen(Screens.current(mc)));
            } else if (t == 20) {
                target.trainGoTo(Screens.current(mc), "RECORDINGS");
            }
            return t >= 100 && target.isTrainScreen(Screens.current(mc));
        });
        shot(probe, "gallery-train-recordings");
        probe.step("gallery: training running", 200, (mc, t) -> {
            if (t == 0) {
                target.trainGoTo(Screens.current(mc), "TRAIN");
            } else if (t == 20) {
                target.trainPrimary(Screens.current(mc));
            }
            return t >= 100;
        });
        shot(probe, "gallery-train-running");

        probe.step("gallery: done", 40, (mc, t) -> {
            if (t == 0) {
                Screens.show(mc, null);
                target.useAiVoice(false);
                target.setEnabled(false);
            }
            return t >= 10;
        });
    }

    /**
     * Moves the cursor to the window's corner first, so no button is
     * highlighted and no tooltip covers the screen. Through GLFW, whose names
     * do not change between Minecraft versions; the probe runs on the render
     * thread, which owns the window's context. Reached by name, since 26.3
     * opens its window with SDL and has no GLFW to compile against; there the
     * cursor stays where it is.
     */
    private static void shot(Probe probe, String name) {
        probe.step("gallery: cursor aside for " + name, 20, (mc, t) -> {
            if (t == 0) {
                try {
                    Class<?> glfw = Reflect.type("org.lwjgl.glfw.GLFW");
                    Reflect.call(glfw, "glfwSetCursorPos", Reflect.call(glfw, "glfwGetCurrentContext"), 1.0D, 1.0D);
                } catch (ClassNotFoundException noGlfw) {
                    Probe.LOG.info("[probe] gallery: no GLFW here, the cursor stays put");
                }
            }
            return t >= 5;
        });
        probe.screenshot(name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyPreset(Target target, String name) throws ReflectiveOperationException {
        Class preset = Reflect.type("org.sawiq." + target.engineKindPackage().replace("ai.engine.", "model.")
                + "VoiceChangerPreset");
        Reflect.call(target.controller(), "applyBuiltInPreset", Enum.valueOf(preset, name));
    }

    private static void useVoice(Target target, String modelId) throws ReflectiveOperationException {
        UnaryOperator<Object> change = settings -> {
            try {
                Object engine = Reflect.call(settings, "voiceEngine");
                return Reflect.call(settings, "withVoiceEngine", Reflect.call(engine, "withModel", modelId));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        };
        Reflect.call(target.controller(), "updateAiSettings", change);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object engineKind(Target target, String name) throws ReflectiveOperationException {
        Class kind = Reflect.type("org.sawiq." + target.engineKindPackage() + "EngineKind");
        return Enum.valueOf(kind, name);
    }

    private static Object installer(Target target) throws ReflectiveOperationException {
        return Reflect.call(Reflect.call(target.controller(), "aiVoice"), "installer");
    }

    /**
     * Puts the installer into the middle of its longest step, as a player
     * sees it for most of an install, and returns what it was before. The
     * install itself is never started.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object showInstalling(Target target) throws ReflectiveOperationException {
        Object installer = installer(target);
        Object before = Reflect.field(installer, "progress");
        Class progressType = before.getClass();
        Class state = Reflect.type(progressType.getName().replace("$Progress", "$State"));
        Constructor<?> constructor = progressType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object installing = constructor.newInstance(Enum.valueOf(state, "INSTALLING"), "packages", -1,
                "Downloading torch (2.3GiB)", System.currentTimeMillis() - 7 * 60_000L);
        setInstallerProgress(target, installing);
        return before;
    }

    private static void setInstallerProgress(Target target, Object progress) throws ReflectiveOperationException {
        Object installer = installer(target);
        java.lang.reflect.Field field = installer.getClass().getDeclaredField("progress");
        field.setAccessible(true);
        field.set(installer, progress);
    }
}
