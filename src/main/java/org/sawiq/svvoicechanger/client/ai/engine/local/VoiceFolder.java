package org.sawiq.svvoicechanger.client.ai.engine.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.sawiq.svvoicechanger.SvVoiceChanger;

/**
 * The player's voices folder, as players actually fill it.
 *
 * <p>The engine reads a voice from a {@code .pth} at the top of the folder or
 * from a folder holding exactly one. What players drop in is often a
 * downloaded {@code .zip}, or a folder that Windows' "Extract all" nested one
 * level deeper; both used to leave the gallery empty with no hint why. This
 * unpacks the one and lifts the other, and tells when the folder changed so
 * the gallery can ask the engine again.</p>
 */
public final class VoiceFolder {
    /** Archives are moved here once unpacked; the engine skips a folder without a voice in it. */
    private static final String IMPORTED_ARCHIVES = ".imported";
    private static final long MAX_UNPACKED_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int NESTED_SEARCH_DEPTH = 4;

    private final Path root;
    /** Archives without a voice, or that failed to unpack, until the game restarts. */
    private final Set<Path> skippedArchives = new HashSet<>();

    public VoiceFolder(Path root) {
        this.root = root;
    }

    /**
     * Changes whenever a voice file is added, removed or replaced, three levels
     * deep; 0 while the folder does not exist.
     */
    public long fingerprint() {
        if (!Files.isDirectory(this.root)) {
            return 0L;
        }
        long fingerprint = 1L;
        try (Stream<Path> paths = Files.walk(this.root, 3)) {
            for (Path path : paths.sorted().toList()) {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                fingerprint = 31L * fingerprint + Objects.hash(this.root.relativize(path).toString(),
                        attributes.size(), attributes.lastModifiedTime().toMillis());
            }
        } catch (IOException exception) {
            return 0L;
        }
        return fingerprint;
    }

    /** Unpacks voice archives and lifts nested voices up to where the engine looks. */
    public void tidy() {
        if (!Files.isDirectory(this.root)) {
            return;
        }
        try (Stream<Path> entries = Files.list(this.root)) {
            for (Path entry : entries.sorted().toList()) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                if (Files.isRegularFile(entry) && name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    unpack(entry);
                } else if (Files.isDirectory(entry)) {
                    liftNestedVoice(entry);
                }
            }
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not look through the voices folder", exception);
        }
    }

    /**
     * Puts the archive's voice into a folder named after it, then moves the
     * archive aside so deleting that folder later does not bring it back.
     * Only the voice's own files are taken, by file name alone, so nothing in
     * the archive can be written outside that folder.
     */
    private void unpack(Path archive) {
        String stem = archive.getFileName().toString().replaceFirst("(?i)\\.zip$", "");
        Path target = this.root.resolve(stem);
        if (Files.exists(target) || this.skippedArchives.contains(archive)) {
            return;
        }
        try {
            if (!unpackVoice(archive, target)) {
                // Looked at once: it is checked again every few seconds and
                // would otherwise fill the log with the same line.
                this.skippedArchives.add(archive);
                SvVoiceChanger.LOGGER.info("{} holds no .pth voice; left as it is", archive.getFileName());
                return;
            }
            // Only once the archive is closed: Windows will not move an open file.
            Path aside = this.root.resolve(IMPORTED_ARCHIVES);
            Files.createDirectories(aside);
            Files.move(archive, aside.resolve(archive.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            SvVoiceChanger.LOGGER.info("Unpacked the voice {} from {}", stem, archive.getFileName());
        } catch (IOException exception) {
            this.skippedArchives.add(archive);
            SvVoiceChanger.LOGGER.warn("Could not unpack the voice archive {}", archive.getFileName(), exception);
        }
    }

    /** @return false when the archive holds no voice */
    private boolean unpackVoice(Path archive, Path target) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<? extends ZipEntry> files = zip.stream()
                    .filter(entry -> !entry.isDirectory() && !entry.getName().contains("__MACOSX"))
                    .toList();
            ZipEntry weights = files.stream()
                    .filter(entry -> hasExtension(entry.getName(), "pth"))
                    .max(Comparator.comparingLong(ZipEntry::getSize))
                    .orElse(null);
            if (weights == null) {
                return false;
            }

            Path staging = Files.createTempDirectory(this.root, ".unpacking");
            try {
                long written = 0L;
                for (ZipEntry entry : files) {
                    if (entry == weights || hasExtension(entry.getName(), "index")) {
                        written += copy(zip, entry, staging, written);
                    }
                }
                Files.move(staging, target);
            } finally {
                deleteQuietly(staging);
            }
            return true;
        }
    }

    private static long copy(ZipFile zip, ZipEntry entry, Path folder, long writtenSoFar) throws IOException {
        Path file = folder.resolve(Path.of(entry.getName()).getFileName().toString());
        try (InputStream input = zip.getInputStream(entry)) {
            long size = Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            if (writtenSoFar + size > MAX_UNPACKED_BYTES) {
                throw new IOException("The voice archive unpacks to more than 4 GB");
            }
            return size;
        }
    }

    /**
     * A folder with no voice of its own but exactly one somewhere below it
     * gets that voice moved up, with the index files beside it.
     */
    private static void liftNestedVoice(Path folder) throws IOException {
        if (!filesWithExtension(folder, 1, "pth").isEmpty()) {
            return;
        }
        List<Path> nested = filesWithExtension(folder, NESTED_SEARCH_DEPTH, "pth");
        if (nested.size() != 1) {
            return;
        }
        Path source = nested.get(0).getParent();
        List<Path> moved = new ArrayList<>(nested);
        moved.addAll(filesWithExtension(source, 1, "index"));
        for (Path file : moved) {
            Files.move(file, folder.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        SvVoiceChanger.LOGGER.info("Moved the voice in {} up to where the engine finds it", folder.getFileName());
    }

    /** Removes a half-unpacked folder; after a successful move there is nothing left to remove. */
    private static void deleteQuietly(Path folder) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not clear {}", folder, exception);
        }
    }

    private static List<Path> filesWithExtension(Path folder, int depth, String extension) throws IOException {
        try (Stream<Path> paths = Files.walk(folder, depth)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> hasExtension(path.getFileName().toString(), extension))
                    .toList();
        }
    }

    private static boolean hasExtension(String name, String extension) {
        return name.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }
}
