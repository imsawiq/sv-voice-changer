package org.sawiq.svvoicechanger.client.audio.dsp;

/**
 * Streaming YIN fundamental frequency detector.
 *
 * <p>Faithful implementation of the algorithm described in
 * de Cheveign&eacute; &amp; Kawahara, &quot;YIN, a fundamental frequency
 * estimator for speech and music&quot;, JASA 2002, steps 2 (difference
 * function), 3 (cumulative mean normalised difference), 4 (absolute
 * threshold) and 5 (parabolic interpolation). Step 6 ("best local estimate")
 * is intentionally omitted; for a streaming voice-changer it is not worth the
 * extra cost.</p>
 */
public final class YinDetector {
    private static final double DEFAULT_THRESHOLD = 0.15D;

    private final float sampleRate;
    private final int bufferSize;
    private final float[] yinBuffer;
    private final double threshold;

    public YinDetector(float sampleRate, int bufferSize) {
        this(sampleRate, bufferSize, DEFAULT_THRESHOLD);
    }

    public YinDetector(float sampleRate, int bufferSize, double threshold) {
        if (bufferSize < 4 || (bufferSize & 1) != 0) {
            throw new IllegalArgumentException("bufferSize must be even and >= 4");
        }
        this.sampleRate = sampleRate;
        this.bufferSize = bufferSize;
        this.yinBuffer = new float[bufferSize / 2];
        this.threshold = threshold;
    }

    /**
     * Run the detector on a buffer of {@code bufferSize} samples.
     *
     * @return pitch in Hz, or {@code -1} if the frame is unvoiced.
     */
    public float getPitch(float[] audioBuffer) {
        if (audioBuffer.length < bufferSize) {
            return -1f;
        }
        int half = yinBuffer.length;

        // Step 2: difference function.
        yinBuffer[0] = 0f;
        for (int tau = 1; tau < half; tau++) {
            float sum = 0f;
            for (int j = 0; j < half; j++) {
                float delta = audioBuffer[j] - audioBuffer[j + tau];
                sum += delta * delta;
            }
            yinBuffer[tau] = sum;
        }

        // Step 3: cumulative mean normalised difference function.
        yinBuffer[0] = 1f;
        float runningSum = 0f;
        for (int tau = 1; tau < half; tau++) {
            runningSum += yinBuffer[tau];
            if (runningSum < 1e-9f) {
                yinBuffer[tau] = 1f;
            } else {
                yinBuffer[tau] = yinBuffer[tau] * tau / runningSum;
            }
        }

        // Step 4: absolute threshold (with descent into the local minimum).
        int tauEstimate = -1;
        for (int tau = 2; tau < half; tau++) {
            if (yinBuffer[tau] < threshold) {
                while (tau + 1 < half && yinBuffer[tau + 1] < yinBuffer[tau]) {
                    tau++;
                }
                tauEstimate = tau;
                break;
            }
        }
        if (tauEstimate == -1) {
            return -1f;
        }

        // Step 5: parabolic interpolation for sub-sample precision.
        float betterTau;
        int x0 = (tauEstimate < 1) ? tauEstimate : tauEstimate - 1;
        int x2 = (tauEstimate + 1 < half) ? tauEstimate + 1 : tauEstimate;
        if (x0 == tauEstimate) {
            betterTau = (yinBuffer[tauEstimate] <= yinBuffer[x2]) ? tauEstimate : x2;
        } else if (x2 == tauEstimate) {
            betterTau = (yinBuffer[tauEstimate] <= yinBuffer[x0]) ? tauEstimate : x0;
        } else {
            float s0 = yinBuffer[x0];
            float s1 = yinBuffer[tauEstimate];
            float s2 = yinBuffer[x2];
            float denom = 2f * (2f * s1 - s2 - s0);
            if (Math.abs(denom) < 1e-9f) {
                betterTau = tauEstimate;
            } else {
                betterTau = tauEstimate + (s2 - s0) / denom;
            }
        }
        if (betterTau <= 0f) {
            return -1f;
        }
        return sampleRate / betterTau;
    }
}
