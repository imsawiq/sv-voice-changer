package org.sawiq.svvoicechanger.client.ai.engine.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What lies in a voice's recordings folder, read straight from the disk so the
 * player sees their files the moment they drop them in, engine or no engine.
 *
 * @param audio  files the engine can learn from, by name
 * @param others files it will skip, by name
 * @param bytes  the audio files' total size
 */
public record Recordings(List<String> audio, List<String> others, long bytes) {
    public static final Recordings EMPTY = new Recordings(List.of(), List.of(), 0L);
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("wav", "mp3", "flac", "ogg");

    public Recordings {
        audio = List.copyOf(audio);
        others = List.copyOf(others);
    }

    public static Recordings scan(Path folder) {
        if (!Files.isDirectory(folder)) {
            return EMPTY;
        }
        List<String> audio = new ArrayList<>();
        List<String> others = new ArrayList<>();
        long bytes = 0L;
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = folder.relativize(file).toString();
                if (AUDIO_EXTENSIONS.contains(extension(name))) {
                    audio.add(name);
                    bytes += Files.size(file);
                } else {
                    others.add(name);
                }
            }
        } catch (IOException exception) {
            return EMPTY;
        }
        return new Recordings(audio, others, bytes);
    }

    public boolean isEmpty() {
        return this.audio.isEmpty();
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
