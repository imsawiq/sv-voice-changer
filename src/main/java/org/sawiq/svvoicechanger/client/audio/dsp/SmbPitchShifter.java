package org.sawiq.svvoicechanger.client.audio.dsp;

import java.util.Arrays;

/**
 * Phase-vocoder pitch shifter using Stephan M. Bernsee's smbPitchShift method
 * (see <a href="http://www.dspdimension.com/admin/pitch-shifting-using-the-ft/">
 * Pitch shifting using the STFT</a>). The original C source code is published
 * by the author under the &quot;Wide Open License&quot;, which allows free use
 * and re-implementation. This class is an independent re-implementation in
 * Java directly from the algorithm description; no GPL code was copied.
 *
 * <p>An optional cepstral spectral-envelope corrector preserves the formant
 * structure of voiced signals while pitch-shifting their harmonic content,
 * matching the technique described in Bernsee's follow-up notes
 * &quot;Pitch shifting and formant preservation&quot;. With formant
 * preservation enabled the shifter converts a vocal track without the
 * &quot;chipmunk&quot; or &quot;giant&quot; artefact normally caused by the
 * formants riding the pitch.</p>
 *
 * <p>Streaming convention: callers feed a sliding window of {@link #fftSize}
 * samples in which only the most recent {@link #stepSize} samples are new on
 * every cycle. Each {@link #process(float[], float[])} call appends one
 * {@code stepSize}-sized block of pitch-shifted output to the supplied
 * destination buffer.</p>
 */
public final class SmbPitchShifter {
    /** Default cepstral lifter cut-off (in cepstral bins). */
    public static final int DEFAULT_LIFTER_CUTOFF = 30;

    private final int fftSize;
    private final int overlap;
    private final int stepSize;
    private final int osamp;
    private final double expectedPhaseInc;
    private final float freqPerBin;
    /**
     * OLA reconstruction gain. The Hann window is applied twice (analysis +
     * synthesis), so each output sample receives the sum over {@code osamp}
     * overlapping windows of {@code w[n]^2}. By the COLA property of the
     * Hann window this sum is the constant {@code 3 * osamp / 8} (independent
     * of {@code n}). Dividing by it gives unity passthrough gain at
     * {@code ratio == 1}. Bernsee's reference C source reaches the same
     * result through a different combination of constants because his FFT
     * routine returns an un-normalised inverse transform; with our
     * {@link Radix2Fft#inverse(float[]) 1/N-normalised} IFFT we have to
     * apply the scale here explicitly.
     */
    private final float olaScale;

    private final Radix2Fft fft;
    private final float[] hannWindow;

    // STFT scratch buffers (all sized once at construction; the hot path is
    // strictly allocation free).
    private final float[] fftData;          // 2 * fftSize  (interleaved complex)
    private final float[] currentMag;       // fftSize / 2
    private final float[] currentFreq;      // fftSize / 2
    private final float[] newMag;           // fftSize / 2
    private final float[] newFreq;          // fftSize / 2
    private final float[] previousPhase;    // fftSize / 2
    private final double[] summedPhase;     // fftSize / 2 (double avoids drift)
    private final float[] outputAccum;      // 2 * fftSize

    // Spectral-envelope (formant preservation) scratch buffers.
    private final float[] envelope;         // fftSize / 2 (linear envelope)
    private final float[] cepstrumWork;     // 2 * fftSize (full-spectrum, complex)

    private float pitchRatio = 1f;
    private boolean formantPreserve = true;
    private int lifterCutoff = DEFAULT_LIFTER_CUTOFF;
    /**
     * Smoothed RMS-matching gain. Bernsee's bin redistribution is not
     * energy-preserving (bins past {@code half/ratio} get discarded for
     * {@code ratio > 1}, multiple bins collapse into one for {@code ratio < 1}),
     * which makes the output noticeably quieter when shifting the pitch up.
     * We compensate by computing the per-frame ratio of input/output spectral
     * energy and applying it as a single global multiplier, low-passed across
     * frames so the correction does not cause audible pumping.
     */
    private float smoothedGainComp = 1f;

    public SmbPitchShifter(float sampleRate, int fftSize, int overlap) {
        if (overlap >= fftSize || overlap < 0) {
            throw new IllegalArgumentException("overlap must be in [0, fftSize)");
        }
        this.fftSize = fftSize;
        this.overlap = overlap;
        this.stepSize = fftSize - overlap;
        if (stepSize <= 0 || fftSize % stepSize != 0) {
            throw new IllegalArgumentException("fftSize must be an integer multiple of stepSize");
        }
        this.osamp = fftSize / stepSize;
        this.expectedPhaseInc = 2.0 * Math.PI * (double) stepSize / fftSize;
        this.freqPerBin = sampleRate / fftSize;
        this.olaScale = 8f / (3f * osamp);

        this.fft = new Radix2Fft(fftSize);
        this.hannWindow = new float[fftSize];
        for (int i = 0; i < fftSize; i++) {
            this.hannWindow[i] = (float) (0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / fftSize));
        }
        this.fftData = new float[2 * fftSize];
        this.currentMag = new float[fftSize / 2];
        this.currentFreq = new float[fftSize / 2];
        this.newMag = new float[fftSize / 2];
        this.newFreq = new float[fftSize / 2];
        this.previousPhase = new float[fftSize / 2];
        this.summedPhase = new double[fftSize / 2];
        this.outputAccum = new float[2 * fftSize];
        this.envelope = new float[fftSize / 2];
        this.cepstrumWork = new float[2 * fftSize];
    }

    public int stepSize() {
        return stepSize;
    }

    public int latencySamples() {
        return overlap;
    }

    public void setPitchRatio(float ratio) {
        this.pitchRatio = ratio;
    }

    public void setFormantPreservation(boolean enabled) {
        this.formantPreserve = enabled;
    }

    public void setLifterCutoff(int cutoff) {
        if (cutoff < 1 || cutoff > fftSize / 4) {
            throw new IllegalArgumentException("lifter cutoff out of range");
        }
        this.lifterCutoff = cutoff;
    }

    /**
     * Process one STFT cycle. Reads the latest {@link #fftSize} samples from
     * {@code window} and writes {@link #stepSize} pitch-shifted samples to
     * {@code outBlock}.
     */
    public void process(float[] window, float[] outBlock) {
        if (window.length < fftSize) {
            throw new IllegalArgumentException("window must hold at least fftSize samples");
        }
        if (outBlock.length < stepSize) {
            throw new IllegalArgumentException("outBlock must hold at least stepSize samples");
        }

        // 1) Window + complex packing.
        for (int i = 0; i < fftSize; i++) {
            fftData[2 * i] = window[i] * hannWindow[i];
            fftData[2 * i + 1] = 0f;
        }

        // 2) Forward FFT.
        fft.forward(fftData);

        // 3) Analyse: magnitude + true frequency per bin.
        int half = fftSize / 2;
        for (int i = 0; i < half; i++) {
            float re = fftData[2 * i];
            float im = fftData[2 * i + 1];
            float mag = (float) (2.0 * Math.sqrt(re * re + im * im));
            float phase = (float) Math.atan2(im, re);

            double dPhase = phase - previousPhase[i];
            previousPhase[i] = phase;

            // Subtract the expected phase advance for this bin.
            dPhase -= (double) i * expectedPhaseInc;

            // Wrap into [-PI, PI].
            long qpd = (long) (dPhase / Math.PI);
            if (qpd >= 0) {
                qpd += qpd & 1L;
            } else {
                qpd -= qpd & 1L;
            }
            dPhase -= Math.PI * qpd;

            // Bin frequency deviation in fractional bins.
            double deviationBins = osamp * dPhase / (2.0 * Math.PI);
            // True bin frequency in Hz.
            double trueFreq = (double) i * freqPerBin + deviationBins * freqPerBin;

            currentMag[i] = mag;
            currentFreq[i] = (float) trueFreq;
        }

        // 4) Compute input spectral energy (before any modification) — used
        //    later for RMS-matching gain compensation.
        double inputEnergy = 0.0;
        for (int i = 0; i < half; i++) {
            inputEnergy += currentMag[i] * currentMag[i];
        }

        // 5) Optional formant preservation: estimate the spectral envelope via
        //    cepstral liftering and divide it out of the magnitudes before the
        //    pitch-shift bin redistribution. The same envelope is multiplied
        //    back in at the *original* (un-shifted) bin index after step 6,
        //    which keeps the formants anchored while the carrier moves.
        boolean useFormant = formantPreserve && pitchRatio != 1f;
        if (useFormant) {
            estimateEnvelope();
            for (int i = 0; i < half; i++) {
                float env = envelope[i];
                if (env > 1e-9f) {
                    currentMag[i] /= env;
                }
            }
        }

        // 6) Pitch shift: redistribute mag/freq across bins by ratio.
        Arrays.fill(newMag, 0f);
        Arrays.fill(newFreq, 0f);
        float ratio = pitchRatio;
        for (int i = 0; i < half; i++) {
            int index = (int) (i * ratio);
            if (index >= 0 && index < half) {
                newMag[index] += currentMag[i];
                newFreq[index] = currentFreq[i] * ratio;
            }
        }

        // Re-apply the envelope at the original bin position so formants stay
        // put.
        if (useFormant) {
            for (int i = 0; i < half; i++) {
                newMag[i] *= envelope[i];
            }
        }

        // 7) RMS-matching gain compensation: bring the output spectral energy
        //    back to the input level. Smoothed across frames so the correction
        //    does not cause pumping at the frame rate (~187 Hz).
        double outputEnergy = 0.0;
        for (int i = 0; i < half; i++) {
            outputEnergy += newMag[i] * newMag[i];
        }
        if (outputEnergy > 1e-12 && inputEnergy > 1e-12) {
            float instantGain = (float) Math.sqrt(inputEnergy / outputEnergy);
            // Clamp to a safe range so a single anomalous frame cannot blow up
            // the level. +/- 12 dB is more than enough for vocal pitch
            // shifting in the [0.5, 2.0] ratio range.
            if (instantGain > 4f) instantGain = 4f;
            else if (instantGain < 0.25f) instantGain = 0.25f;
            // One-pole low-pass: tc ~ 4 frames (~21 ms at 48 kHz / 256-sample
            // hops) — fast enough to track musical dynamics, slow enough that
            // the correction itself is inaudible.
            smoothedGainComp += 0.30f * (instantGain - smoothedGainComp);
        }
        for (int i = 0; i < half; i++) {
            newMag[i] *= smoothedGainComp;
        }

        // 6) Synthesise: cumulate phase and rebuild complex spectrum.
        for (int i = 0; i < half; i++) {
            float mag = newMag[i];
            double tmp = newFreq[i];
            tmp -= (double) i * freqPerBin;
            tmp /= freqPerBin;
            tmp = 2.0 * Math.PI * tmp / osamp;
            tmp += (double) i * expectedPhaseInc;
            summedPhase[i] += tmp;
            double phase = summedPhase[i];
            fftData[2 * i] = (float) (mag * Math.cos(phase));
            fftData[2 * i + 1] = (float) (mag * Math.sin(phase));
        }
        // Zero negative-frequency bins (mirror image of positive).
        for (int i = half; i < fftSize; i++) {
            fftData[2 * i] = 0f;
            fftData[2 * i + 1] = 0f;
        }

        // 7) Inverse FFT.
        fft.inverse(fftData);

        // 8) Window + overlap-add into the accumulator. See `olaScale` doc.
        for (int i = 0; i < fftSize; i++) {
            outputAccum[i] += hannWindow[i] * fftData[2 * i] * olaScale;
        }

        // 9) Emit the first stepSize samples.
        System.arraycopy(outputAccum, 0, outBlock, 0, stepSize);

        // 10) Shift the accumulator left by stepSize, zeroing the new tail.
        System.arraycopy(outputAccum, stepSize, outputAccum, 0, fftSize);
        for (int i = fftSize; i < fftSize + stepSize; i++) {
            outputAccum[i] = 0f;
        }
    }

    /**
     * Estimate the smoothed spectral envelope through cepstral liftering.
     *
     * <p>1. Build the full mirrored log magnitude spectrum.
     * 2. IFFT &rarr; real cepstrum.
     * 3. Keep only low-quefrency coefficients (formant region).
     * 4. FFT &rarr; smoothed log magnitude.
     * 5. exp() &rarr; linear envelope.</p>
     */
    private void estimateEnvelope() {
        int half = fftSize / 2;

        // Build full spectrum log magnitude with Hermitian symmetry.
        for (int i = 0; i < half; i++) {
            float mag = currentMag[i];
            float logMag = (float) Math.log(Math.max(1e-6f, mag));
            cepstrumWork[2 * i] = logMag;
            cepstrumWork[2 * i + 1] = 0f;
        }
        // Mirror to upper half (conjugate symmetric so cepstrum stays real).
        for (int i = half; i < fftSize; i++) {
            int mirror = fftSize - i;
            if (mirror < 0 || mirror >= half) {
                mirror = 0;
            }
            cepstrumWork[2 * i] = cepstrumWork[2 * mirror];
            cepstrumWork[2 * i + 1] = 0f;
        }

        // Cepstrum = IFFT of log magnitude.
        fft.inverse(cepstrumWork);

        // Liftering: keep coefficients [0, M) and [N - M, N). Zero the rest.
        int m = lifterCutoff;
        for (int i = m; i < fftSize - m; i++) {
            cepstrumWork[2 * i] = 0f;
            cepstrumWork[2 * i + 1] = 0f;
        }

        // Forward FFT to recover smoothed log magnitude.
        fft.forward(cepstrumWork);

        // exp() to convert back to linear envelope.
        for (int i = 0; i < half; i++) {
            float smoothedLog = cepstrumWork[2 * i];
            envelope[i] = (float) Math.exp(smoothedLog);
        }
    }
}

