package org.sawiq.svvoicechanger.client.ai.install;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The installed engine, run by the mod: started without a window when the AI
 * voice needs it, and stopped when it does not or the game closes.
 *
 * <p>The engine is told the game's process id and exits on its own if the
 * game goes away without saying goodbye, so a crash cannot leave it holding
 * the GPU in the background.</p>
 */
public final class EngineProcess {
    private static final int LOG_TAIL_BYTES = 4096;

    private Process process;
    private long startedAt;

    /**
     * How a run the mod did not stop ended.
     *
     * @param exitCode   the engine's exit code
     * @param uptimeMillis how long it ran; a few seconds means it could not start at all
     * @param lastLogLine its last words, for the log and the advanced view
     */
    public record Exit(int exitCode, long uptimeMillis, String lastLogLine) {
    }

    public synchronized boolean isRunning() {
        return this.process != null && this.process.isAlive();
    }

    public synchronized void start(int port) throws IOException {
        if (isRunning()) {
            return;
        }

        Files.createDirectories(EngineFiles.logDirectory());
        Files.createDirectories(EngineFiles.modelsDirectory());
        Files.createDirectories(EngineFiles.datasetsDirectory());
        List<String> command = List.of(
                EngineFiles.python().toString(), "-m", "vcengine",
                "--port", Integer.toString(port),
                "--models", EngineFiles.modelsDirectory().toString(),
                "--datasets", EngineFiles.datasetsDirectory().toString(),
                "--parent-pid", Long.toString(ProcessHandle.current().pid()));
        this.process = new ProcessBuilder(command)
                .directory(EngineFiles.engineDirectory().toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputLog().toFile())
                .start();
        this.startedAt = System.currentTimeMillis();
    }

    /**
     * Reports, once, that the engine exited although nobody asked it to.
     *
     * @return {@code null} while it runs, after it was stopped, or once reported
     */
    public synchronized Exit takeUnexpectedExit() {
        if (this.process == null || this.process.isAlive()) {
            return null;
        }
        Exit exit = new Exit(this.process.exitValue(), System.currentTimeMillis() - this.startedAt, lastLogLine());
        this.process = null;
        return exit;
    }

    /**
     * Asks the engine to stop and returns at once; one that has not exited
     * three seconds later is killed. Never blocks, because it is called from
     * the client thread.
     */
    public synchronized void stop() {
        if (this.process == null) {
            return;
        }
        Process running = this.process;
        this.process = null;
        running.destroy();
        running.onExit()
                .completeOnTimeout(running, 3, TimeUnit.SECONDS)
                .thenAccept(exited -> {
                    if (exited.isAlive()) {
                        exited.descendants().forEach(ProcessHandle::destroyForcibly);
                        exited.destroyForcibly();
                    }
                });
    }

    private static Path outputLog() {
        return EngineFiles.logDirectory().resolve("engine-output.log");
    }

    /** The last non-empty line the engine printed; a Python traceback ends with the reason. */
    private static String lastLogLine() {
        try (RandomAccessFile file = new RandomAccessFile(outputLog().toFile(), "r")) {
            long start = Math.max(0L, file.length() - LOG_TAIL_BYTES);
            byte[] tail = new byte[(int) (file.length() - start)];
            file.seek(start);
            file.readFully(tail);
            String[] lines = new String(tail, StandardCharsets.UTF_8).split("\\R");
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!lines[i].isBlank()) {
                    return lines[i].strip();
                }
            }
        } catch (IOException ignored) {
            // No log to read: the exit code is all there is.
        }
        return "";
    }
}
