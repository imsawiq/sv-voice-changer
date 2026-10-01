package probe;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a production client through the places players open the voice
 * changer: the studio over the title screen, the voice mod's own menu in a
 * world, the studio from there, both studio modes, with the effect running.
 *
 * <p>Compiled against the Minecraft version it runs on and remapped like any
 * released mod, so it sees the game exactly as the voice changer does. The
 * voice changer and voice mod classes are reached by name, which is safe: only
 * Minecraft's own names change between dev and production.</p>
 *
 * <p>Ends with "[probe] PASS" or "[probe] FAIL" in the log, then quits. A crash
 * while drawing a screen ends the game before either line is written.</p>
 */
public final class Probe {
    static final Logger LOG = LoggerFactory.getLogger("probe");
    private static final String WORLD = "probe";
    private static final long FROZEN_AFTER_NANOS = 30_000_000_000L;

    private final Deque<Step> steps = new ArrayDeque<>();
    private int stepTicks;
    private volatile boolean finished;
    /** Zero until the first tick: the initial resource load can take longer than the limit. */
    private volatile long lastTickNanos;
    private Screen lastScreen;

    /**
     * Builds the steps for the mod named by -Dprobe.target, a {@link Target}
     * class; the loader entry point then calls {@link #tick} every client tick.
     */
    public Probe() {
        String targetClass = System.getProperty("probe.target", "");
        try {
            buildSteps((Target) Class.forName(targetClass).getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("No probe target '" + targetClass + "'", exception);
        }
        startWatchdog();
    }

    /**
     * Client ticks stop when the game hangs, and so would every timeout above,
     * leaving a window that never closes. This notices, writes every thread's
     * stack to the log so the hang can be read afterwards, and ends the game.
     */
    private void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (!this.finished) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    return;
                }
                long lastTick = this.lastTickNanos;
                if (lastTick != 0 && System.nanoTime() - lastTick > FROZEN_AFTER_NANOS) {
                    StringBuilder dump = new StringBuilder();
                    Thread.getAllStackTraces().forEach((thread, stack) -> {
                        dump.append('"').append(thread.getName()).append("\" ").append(thread.getState()).append('\n');
                        for (StackTraceElement frame : stack) {
                            dump.append("    at ").append(frame).append('\n');
                        }
                    });
                    LOG.error("[probe] FAIL frozen: no client tick for 30 s\n{}", dump);
                    Runtime.getRuntime().halt(3);
                }
            }
        }, "probe-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void buildSteps(Target target) {
        step("title screen", 2400, (mc, t) -> !Screens.isLoading(mc) && Screens.current(mc) instanceof TitleScreen && t > 100);
        step("voice changer initialized", 1200, (mc, t) -> target.isInitialized());
        step("key bindings registered and named", 20, (mc, t) -> {
            List<String> problems = target.bindingProblems(mc);
            if (!problems.isEmpty()) {
                throw new IllegalStateException(String.join("; ", problems));
            }
            return true;
        });

        step("studio over the title screen", 200, (mc, t) -> {
            if (t == 0) {
                Screens.show(mc, target.newStudio(Screens.current(mc)));
            }
            return t >= 40 && target.isStudio(Screens.current(mc));
        });
        screenshot("title-studio");
        step("advanced mode over the title screen", 200, (mc, t) -> {
            if (t == 0) {
                target.switchToAdvanced(Screens.current(mc));
            }
            return t >= 40 && target.isStudio(Screens.current(mc));
        });
        screenshot("title-studio-advanced");
        step("close the studio", 100, (mc, t) -> {
            if (t == 0) {
                Screens.current(mc).onClose();
            }
            return t >= 5 && Screens.current(mc) instanceof TitleScreen;
        });

        step("join the world", 4800, (mc, t) -> {
            if (t == 0) {
                mc.createWorldOpenFlows().openWorld(WORLD, () -> fail(mc, "world '" + WORLD + "' refused to open", null));
            }
            if (Screens.current(mc) instanceof BackupConfirmScreen backupPrompt) {
                skipBackup(backupPrompt);
            }
            return mc.player != null && mc.level != null && Screens.current(mc) == null;
        });
        step("voice connected", 1200, (mc, t) -> t >= 100 && target.isVoiceConnected());

        target.addWorldSteps(this);

        step("turn the effect on", 200, (mc, t) -> {
            if (t == 0) {
                LOG.info("[probe] audio before: {}", target.describeAudio());
                target.setEnabled(true);
            }
            if (t == 100) {
                LOG.info("[probe] audio after: {}", target.describeAudio());
            }
            return t >= 100 && target.isStudio(Screens.current(mc));
        });
        screenshot("world-studio-effect-on");
        step("self-listen runs the microphone through the effect", 200, (mc, t) -> {
            if (t == 0) {
                target.setSelfListen(true);
            }
            if (t >= 60 && target.isAudioFlowing()) {
                LOG.info("[probe] audio with self-listen: {}", target.describeAudio());
                return true;
            }
            return false;
        });
        screenshot("world-studio-self-listen");
        step("self-listen off", 100, (mc, t) -> {
            if (t == 0) {
                target.setSelfListen(false);
            }
            return t >= 60 && !target.isAudioFlowing();
        });
        step("advanced mode in the world", 200, (mc, t) -> {
            if (t == 0) {
                target.switchToAdvanced(Screens.current(mc));
            }
            return t >= 40 && target.isStudio(Screens.current(mc));
        });
        screenshot("world-studio-advanced");
        step("close the studio in the world", 100, (mc, t) -> {
            if (t == 0) {
                Screens.current(mc).onClose();
            }
            return t >= 10 && !target.isStudio(Screens.current(mc));
        });
        step("turn the effect off", 100, (mc, t) -> {
            if (t == 0) {
                target.setEnabled(false);
            }
            return t >= 20;
        });
    }

    /**
     * A release newer than the one that wrote the test world may ask for a
     * backup before upgrading it; answer "skip" the way a player would. The
     * field is found by its type, since its name is not the same in every
     * environment.
     */
    private static void skipBackup(BackupConfirmScreen prompt) throws ReflectiveOperationException {
        for (Field field : BackupConfirmScreen.class.getDeclaredFields()) {
            if (field.getType() == BackupConfirmScreen.Listener.class) {
                field.setAccessible(true);
                ((BackupConfirmScreen.Listener) field.get(prompt)).proceed(false, false);
                return;
            }
        }
        throw new IllegalStateException("The backup prompt has no listener to answer");
    }

    void step(String name, int timeoutTicks, Action action) {
        this.steps.add(new Step(name, timeoutTicks, action));
    }

    void screenshot(String name) {
        step("screenshot " + name, 40, (mc, t) -> {
            if (t == 0) {
                Screens.screenshot(mc, message -> LOG.info("[probe] screenshot {}: {}", name, message.getString()));
            }
            return t >= 10;
        });
    }

    public void tick(Minecraft mc) {
        this.lastTickNanos = System.nanoTime();
        if (this.finished) {
            return;
        }

        Screen screen = Screens.current(mc);
        if (screen != this.lastScreen) {
            LOG.info("[probe] screen: {}", describe(screen));
            this.lastScreen = screen;
        }

        Step step = this.steps.peek();
        if (step == null) {
            this.finished = true;
            LOG.info("[probe] PASS");
            mc.stop();
            return;
        }

        try {
            if (step.action().tick(mc, this.stepTicks++)) {
                LOG.info("[probe] ok: {}", step.name());
                this.steps.poll();
                this.stepTicks = 0;
            } else if (this.stepTicks > step.timeoutTicks()) {
                fail(mc, "timed out: " + step.name() + " (screen: " + describe(Screens.current(mc)) + ")", null);
            }
        } catch (Throwable throwable) {
            fail(mc, "threw in: " + step.name(), throwable);
        }
    }

    void fail(Minecraft mc, String reason, Throwable cause) {
        if (this.finished) {
            return;
        }
        this.finished = true;
        LOG.error("[probe] FAIL {}", reason, cause);
        mc.stop();
    }

    static String describe(Screen screen) {
        return screen == null ? "none" : screen.getClass().getName();
    }

    @FunctionalInterface
    interface Action {
        /** Called every client tick until it returns true; t counts ticks spent in this step. */
        boolean tick(Minecraft mc, int t) throws Exception;
    }

    private record Step(String name, int timeoutTicks, Action action) {
    }
}
