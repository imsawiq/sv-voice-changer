package org.sawiq.svvoicechanger.client.ai.install;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.ai.engine.SystemProxy;

/**
 * Installs the Voice Changer Engine for the player, after they agreed to it in
 * the AI tab, and removes it again.
 *
 * <p>Nothing is installed system-wide. uv, a single program, is downloaded
 * into the engine's folder and fetches Python and the engine's packages into
 * that same folder, so deleting the folder removes every trace. Every
 * download is checked against a hash built into the mod before it is used.</p>
 */
public final class EngineInstaller {
    public enum State {
        UNSUPPORTED,
        NOT_INSTALLED,
        INSTALLING,
        INSTALLED,
        FAILED
    }

    /**
     * @param step    translation key suffix of the current step, empty when idle
     * @param percent 0..100 for downloads with a known size, -1 otherwise
     * @param detail    the last line uv printed, or the failure, never translated
     * @param startedAt when this install began, in epoch millis; 0 when nothing is being installed
     */
    public record Progress(State state, String step, int percent, String detail, long startedAt) {
        /** 1-based position of the current step in {@link #STEPS}, or 0 before the first. */
        public int stepNumber() {
            return STEPS.indexOf(this.step) + 1;
        }
    }

    /** The install steps in order, as their translation key suffixes. */
    public static final List<String> STEPS = List.of("download_uv", "download_engine", "python", "packages");

    private static final HttpClient DOWNLOADS = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(SystemProxy.selector())
            .build();
    private static final int MAX_DETAIL_LENGTH = 160;
    private static final long DISK_CHECK_INTERVAL_MILLIS = 5_000L;

    private volatile Progress progress;
    /** The last install's end, FAILED or INSTALLED, until someone takes it to tell the player. */
    private volatile Progress outcome;
    private volatile long lastDiskCheck = System.currentTimeMillis();
    /** Detected once, in the background: nvidia-smi can take a few seconds to answer. */
    private volatile PackageSet packageSet;
    private volatile Process child;
    private volatile long installStartedAt;
    private volatile boolean cancelled;
    private Thread worker;

    public EngineInstaller() {
        this.progress = idle();
        if (EngineFiles.isSupported()) {
            Thread detection = new Thread(() -> this.packageSet = PackageSet.detect(), "Voice changer GPU detection");
            detection.setDaemon(true);
            detection.start();
        }
    }

    /** The package set for this computer's graphics card, or null while it is still being found out. */
    public PackageSet packageSet() {
        return this.packageSet;
    }

    /**
     * While nothing is being installed, the folder is looked at again every
     * few seconds: the engine may be put there or deleted by hand while the
     * game runs, and a state read once at startup would never see it.
     */
    public Progress progress() {
        Progress current = this.progress;
        long now = System.currentTimeMillis();
        boolean idle = current.state() == State.NOT_INSTALLED || current.state() == State.INSTALLED;
        if (idle && now - this.lastDiskCheck > DISK_CHECK_INTERVAL_MILLIS) {
            this.lastDiskCheck = now;
            synchronized (this) {
                if (this.progress == current) {
                    this.progress = idle();
                }
            }
        }
        return this.progress;
    }

    public boolean isInstalled() {
        return progress().state() == State.INSTALLED;
    }

    /**
     * Starts installing in the background. A note is left in the engine folder
     * until the install ends, so one cut short by closing the game carries on
     * at the next start: the player already agreed, and uv keeps what it
     * downloaded, so nothing is fetched twice.
     */
    public synchronized void install() {
        if (this.progress.state() == State.INSTALLING || !EngineFiles.isSupported()) {
            return;
        }
        try {
            Files.createDirectories(EngineFiles.root());
            Files.writeString(EngineFiles.pendingInstallMarker(), EngineFiles.ENGINE_VERSION, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not note the engine install for the next start", exception);
        }
        this.cancelled = false;
        this.installStartedAt = System.currentTimeMillis();
        this.worker = new Thread(this::runInstall, "Voice changer engine install");
        this.worker.setDaemon(true);
        this.progress = installing("start", -1, "");
        this.worker.start();
    }

    /** Carries on with an install the game was closed during; does nothing otherwise. */
    public void resumeInterruptedInstall() {
        if (EngineFiles.isSupported() && Files.isRegularFile(EngineFiles.pendingInstallMarker()) && !isInstalled()) {
            SvVoiceChanger.LOGGER.info("Carrying on with the voice changer engine install from the last session");
            install();
        }
    }

    /** The install that just ended, once; null while nothing new ended. */
    public Progress takeOutcome() {
        Progress ended = this.outcome;
        this.outcome = null;
        return ended;
    }

    /** Stops the install; the player chose to, so it is not carried on at the next start either. */
    public void cancel() {
        this.cancelled = true;
        forgetPendingInstall();
        stopChild();
    }

    /** Stops the install for the game closing; the note stays, so the next start carries on. */
    public void interrupt() {
        this.cancelled = true;
        stopChild();
    }

    private void stopChild() {
        Process process = this.child;
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    /** Deletes the whole engine folder. The caller stops the engine first. */
    public synchronized void uninstall() throws IOException {
        cancel();
        deleteTree(EngineFiles.root());
        this.progress = idle();
    }

    // --- Steps ----------------------------------------------------------------

    private void runInstall() {
        try {
            if (EngineFiles.ENGINE_SHA256.isBlank()) {
                throw new IOException("The engine release is not published yet");
            }
            Files.createDirectories(EngineFiles.root());

            Path uvZip = download("download_uv", EngineFiles.UV_URL, EngineFiles.UV_SHA256);
            unzipFile(uvZip, "uv.exe", EngineFiles.uvExecutable());
            Files.deleteIfExists(uvZip);

            Path engineZip = download("download_engine", EngineFiles.ENGINE_URL, EngineFiles.ENGINE_SHA256);
            replaceEngineKeepingModels(engineZip);
            Files.deleteIfExists(engineZip);

            runUv("python", List.of("venv", "--python", "3.12", "--allow-existing", ".venv"));
            PackageSet packages = this.packageSet != null ? this.packageSet : PackageSet.detect();
            this.packageSet = packages;
            runUv("packages", List.of("pip", "install", "--python", ".venv\\Scripts\\python.exe",
                    "-r", packages.requirementsFile()));

            writeMarker(packages);
            finish(new Progress(State.INSTALLED, "", 100, "", 0L));
        } catch (IOException | RuntimeException exception) {
            String reason = this.cancelled ? "cancelled" : describe(exception);
            if (!this.cancelled) {
                SvVoiceChanger.LOGGER.warn("Voice changer engine install failed: {}", reason);
            }
            finish(new Progress(State.FAILED, "", -1, reason, 0L));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            finish(new Progress(State.FAILED, "", -1, "cancelled", 0L));
        } finally {
            this.child = null;
        }
    }

    /**
     * A cancel is the player's own doing and needs no message afterwards. A
     * failure is told instead of retried at every start: the player sees why
     * and presses Install again when it suits them.
     */
    private void finish(Progress ended) {
        this.progress = ended;
        if (!this.cancelled) {
            forgetPendingInstall();
            this.outcome = ended;
        }
    }

    private Progress installing(String step, int percent, String detail) {
        return new Progress(State.INSTALLING, step, percent, detail, this.installStartedAt);
    }

    private static void forgetPendingInstall() {
        try {
            Files.deleteIfExists(EngineFiles.pendingInstallMarker());
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not clear the engine install note", exception);
        }
    }

    /** Streams to a temporary file, hashing as it goes, and refuses a file that does not match. */
    private Path download(String step, String url, String expectedSha256) throws IOException, InterruptedException {
        this.progress = installing(step, 0, "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30)).GET().build();
        HttpResponse<InputStream> response = DOWNLOADS.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Download failed, HTTP " + response.statusCode() + ": " + url);
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Path target = Files.createTempFile(EngineFiles.root(), "download", ".zip");
        MessageDigest digest = sha256();
        try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (this.cancelled) {
                    throw new IOException("cancelled");
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                done += read;
                int percent = total > 0 ? (int) (done * 100 / total) : -1;
                this.progress = installing(step, percent, megabytes(done, total));
            }
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        }

        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            Files.deleteIfExists(target);
            throw new IOException("The downloaded file does not match the expected hash: " + url);
        }
        return target;
    }

    /**
     * Unpacks a new engine over the old one. Voice models the player added
     * survive an update; everything else is replaced.
     */
    private void replaceEngineKeepingModels(Path zip) throws IOException {
        Path engine = EngineFiles.engineDirectory();
        Path keptModels = EngineFiles.root().resolve("models-kept");
        if (Files.isDirectory(EngineFiles.bundledModelsDirectory())) {
            deleteTree(keptModels);
            Files.move(EngineFiles.bundledModelsDirectory(), keptModels);
        }
        deleteTree(engine);
        unzipAll(zip, engine);
        if (Files.isDirectory(keptModels)) {
            deleteTree(EngineFiles.bundledModelsDirectory());
            Files.move(keptModels, EngineFiles.bundledModelsDirectory());
        }
        Files.createDirectories(EngineFiles.bundledModelsDirectory());
    }

    /**
     * Runs uv in the engine folder with its caches and Python kept inside the
     * engine's own folder. Its output is the progress detail, line by line.
     */
    private void runUv(String step, List<String> arguments) throws IOException, InterruptedException {
        this.progress = installing(step, -1, "");
        List<String> command = Stream.concat(Stream.of(EngineFiles.uvExecutable().toString()), arguments.stream()).toList();
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(EngineFiles.engineDirectory().toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.put("UV_CACHE_DIR", EngineFiles.root().resolve("cache").toString());
        environment.put("UV_PYTHON_INSTALL_DIR", EngineFiles.root().resolve("python").toString());
        // Never a Python the player installed: the engine would break the day
        // they update or remove it, and its folder would not hold everything.
        environment.put("UV_MANAGED_PYTHON", "1");
        environment.put("UV_NO_PROGRESS", "1");
        Optional<InetSocketAddress> proxy = SystemProxy.address();
        proxy.ifPresent(address -> {
            String value = "http://" + address.getHostString() + ":" + address.getPort();
            environment.put("HTTPS_PROXY", value);
            environment.put("HTTP_PROXY", value);
        });

        Files.createDirectories(EngineFiles.logDirectory());
        Path log = EngineFiles.logDirectory().resolve("install.log");
        Process process = builder.start();
        this.child = process;
        try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = output.readLine()) != null) {
                Files.writeString(log, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (!line.isBlank()) {
                    this.progress = installing(step, -1, shorten(line.strip()));
                }
            }
        }
        int exit = process.waitFor();
        if (this.cancelled) {
            throw new IOException("cancelled");
        }
        if (exit != 0) {
            throw new IOException("uv " + arguments.get(0) + " failed (exit " + exit + "), see " + log);
        }
    }

    // --- Files ----------------------------------------------------------------

    private Progress idle() {
        if (!EngineFiles.isSupported()) {
            return new Progress(State.UNSUPPORTED, "", -1, "", 0L);
        }
        return installedVersion().isPresent()
                && Files.isRegularFile(EngineFiles.python())
                ? new Progress(State.INSTALLED, "", 100, "", 0L)
                : new Progress(State.NOT_INSTALLED, "", -1, "", 0L);
    }

    private static Optional<String> installedVersion() {
        Path marker = EngineFiles.marker();
        if (!Files.isRegularFile(marker)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(marker)) {
            properties.load(input);
            return Optional.ofNullable(properties.getProperty("version"));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static void writeMarker(PackageSet packages) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", EngineFiles.ENGINE_VERSION);
        properties.setProperty("packages", packages.name());
        try (OutputStream output = Files.newOutputStream(EngineFiles.marker())) {
            properties.store(output, "Voice Changer Engine, installed by the voice changer mod");
        }
    }

    private static void unzipFile(Path zip, String name, Path target) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory() && Path.of(entry.getName()).getFileName().toString().equals(name)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException(name + " not found in " + zip.getFileName());
    }

    /** Refuses entries that would land outside the target folder. */
    private static void unzipAll(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path destination = root.resolve(entry.getName()).normalize();
                if (!destination.startsWith(root)) {
                    throw new IOException("Unsafe path in the engine archive: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is missing from this Java", exception);
        }
    }

    private static String megabytes(long done, long total) {
        long doneMb = done / (1024 * 1024);
        return total > 0 ? doneMb + " / " + total / (1024 * 1024) + " MB" : doneMb + " MB";
    }

    private static String shorten(String line) {
        return line.length() <= MAX_DETAIL_LENGTH ? line : line.substring(0, MAX_DETAIL_LENGTH) + "...";
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
