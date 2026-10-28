package org.sawiq.svvoicechanger.client.ai.audio;

/**
 * Integer-ratio sample rate conversion: up by {@code up}, then down by
 * {@code down}, through one windowed-sinc low-pass filter.
 *
 * <p>Written as a polyphase filter so the zero samples that upsampling would
 * insert are never multiplied. Streaming: the filter history carries over
 * between calls, so audio arriving in pieces converts exactly as it would in
 * one piece. Only the two ratios ElevenLabs needs are used, 48 to 16 kHz on
 * the way out and 24 to 48 kHz on the way back.</p>
 *
 * <p>Not thread safe: one instance belongs to one stream.</p>
 */
public final class Resampler {
    /** Enough to keep aliasing well below what a voice chat codec preserves. */
    private static final int TAPS_PER_PHASE = 24;
    /** Cutoff as a fraction of the lower Nyquist frequency, leaving a transition band. */
    private static final double CUTOFF_FRACTION = 0.9D;

    private final int up;
    private final int down;
    private final float[][] phases;
    private final float[] history = new float[TAPS_PER_PHASE];
    private long inputCount;
    private long outputCount;

    public Resampler(int up, int down) {
        if (up < 1 || down < 1) {
            throw new IllegalArgumentException("Ratios must be positive: " + up + "/" + down);
        }
        this.up = up;
        this.down = down;
        this.phases = designPhases(up, down);
    }

    public static Resampler from48kTo16k() {
        return new Resampler(1, 3);
    }

    public static Resampler from24kTo48k() {
        return new Resampler(2, 1);
    }

    /** Converts a whole buffer in one go, for audio that is complete already. */
    public static short[] convertAll(Resampler resampler, short[] input) {
        return resampler.process(input, 0, input.length);
    }

    public short[] process(short[] input, int offset, int length) {
        int capacity = (int) ((length * (long) this.up) / this.down) + 2;
        short[] output = new short[capacity];
        int written = 0;

        for (int i = offset; i < offset + length; i++) {
            System.arraycopy(this.history, 1, this.history, 0, TAPS_PER_PHASE - 1);
            this.history[TAPS_PER_PHASE - 1] = input[i] / 32_768f;
            this.inputCount++;

            // An output sample n sits at time n*down on the upsampled grid and
            // needs input floor(n*down/up) as its newest tap. Every output
            // whose newest tap is the sample that just arrived is due now.
            while (written < capacity && (this.outputCount * this.down) / this.up == this.inputCount - 1) {
                int phase = (int) ((this.outputCount * this.down) % this.up);
                output[written++] = toShort(convolve(this.phases[phase]));
                this.outputCount++;
            }
        }

        if (written == capacity) {
            return output;
        }
        short[] trimmed = new short[written];
        System.arraycopy(output, 0, trimmed, 0, written);
        return trimmed;
    }

    private float convolve(float[] coefficients) {
        float sum = 0f;
        for (int tap = 0; tap < TAPS_PER_PHASE; tap++) {
            sum += coefficients[tap] * this.history[TAPS_PER_PHASE - 1 - tap];
        }
        return sum;
    }

    /**
     * Splits one low-pass prototype into {@code up} phases. Phase p holds taps
     * p, p+up, p+2*up and so on, which are the only ones that ever land on a
     * real input sample for outputs at that phase.
     */
    private static float[][] designPhases(int up, int down) {
        int length = TAPS_PER_PHASE * up;
        double cutoff = CUTOFF_FRACTION * 0.5D / Math.max(up, down);
        double center = (length - 1) / 2.0D;

        float[][] phases = new float[up][TAPS_PER_PHASE];
        for (int i = 0; i < length; i++) {
            double t = i - center;
            double sinc = t == 0.0D ? 2.0D * cutoff : Math.sin(2.0D * Math.PI * cutoff * t) / (Math.PI * t);
            double window = 0.42D
                    - 0.5D * Math.cos(2.0D * Math.PI * i / (length - 1))
                    + 0.08D * Math.cos(4.0D * Math.PI * i / (length - 1));
            // The factor of up restores the level that zero stuffing divides away.
            phases[i % up][i / up] = (float) (sinc * window * up);
        }
        return phases;
    }

    private static short toShort(float value) {
        float clamped = Math.max(-1f, Math.min(1f, value));
        return (short) Math.round(clamped * 32_767f);
    }
}
