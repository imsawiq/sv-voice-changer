package org.sawiq.svvoicechanger.client.ai.audio;

/**
 * The one audio shape the AI voice works in: 20 ms frames of mono 16-bit PCM
 * at 48 kHz, which is what both voice mods capture.
 *
 * <p>Every engine speaks something else on the wire - ElevenLabs takes 16 kHz
 * and sends back 24 kHz, the local engines take whole chunks - so conversion
 * happens at the engine's edge and everything between the microphone and the
 * engine only ever sees these frames.</p>
 */
public final class AudioFrames {
    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_SAMPLES = 960;
    public static final int FRAME_MILLIS = 20;

    private AudioFrames() {
    }

    /** How many whole frames cover the given duration, at least one. */
    public static int framesFor(int millis) {
        return Math.max(1, millis / FRAME_MILLIS);
    }

    public static short[] silence() {
        return new short[FRAME_SAMPLES];
    }

    /** Little-endian bytes, which is what every engine here expects for raw PCM. */
    public static byte[] toLittleEndian(short[] samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[2 * i] = (byte) samples[i];
            bytes[2 * i + 1] = (byte) (samples[i] >> 8);
        }
        return bytes;
    }

    /** Reads {@code length} bytes; a trailing odd byte is ignored. */
    public static short[] fromLittleEndian(byte[] bytes, int offset, int length) {
        short[] samples = new short[length / 2];
        for (int i = 0; i < samples.length; i++) {
            int low = bytes[offset + 2 * i] & 0xFF;
            int high = bytes[offset + 2 * i + 1];
            samples[i] = (short) ((high << 8) | low);
        }
        return samples;
    }

    /** Averages interleaved channels into one. Returns the input itself when it already is mono. */
    public static short[] toMono(short[] interleaved, int channels) {
        if (channels <= 1) {
            return interleaved;
        }

        short[] mono = new short[interleaved.length / channels];
        for (int frame = 0; frame < mono.length; frame++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += interleaved[frame * channels + channel];
            }
            mono[frame] = (short) (sum / channels);
        }
        return mono;
    }

    /** Writes a mono frame into every channel of an interleaved block of the same length in frames. */
    public static void copyToAllChannels(short[] mono, short[] interleaved, int channels) {
        int frames = Math.min(mono.length, interleaved.length / Math.max(1, channels));
        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channels; channel++) {
                interleaved[frame * channels + channel] = mono[frame];
            }
        }
    }

    /** Peak level from 0 to 1, for meters. */
    public static double peakOf(short[] samples) {
        int peak = 0;
        for (short sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak / 32_768.0D;
    }
}
