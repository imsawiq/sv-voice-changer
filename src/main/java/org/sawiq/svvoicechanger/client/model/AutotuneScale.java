package org.sawiq.svvoicechanger.client.model;

/**
 * Note sets the autotune is allowed to snap to.
 *
 * <p>The ordinal is persisted in preset files through
 * {@link VoiceParameter#AUTOTUNE_SCALE}, so the order must not change.</p>
 */
public enum AutotuneScale {
    CHROMATIC("chromatic", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
    MAJOR("major", 0, 2, 4, 5, 7, 9, 11),
    MINOR("minor", 0, 2, 3, 5, 7, 8, 10);

    private static final AutotuneScale[] VALUES = values();

    private final String key;
    private final int[] semitones;

    AutotuneScale(String key, int... semitones) {
        this.key = key;
        this.semitones = semitones;
    }

    /** Scale degrees in semitones above the root. */
    public int[] semitones() {
        return this.semitones;
    }

    public String translationKey() {
        return "svvoicechanger.studio.autotune_scale." + this.key;
    }

    public static AutotuneScale byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : CHROMATIC;
    }

    public static int count() {
        return VALUES.length;
    }
}
