package org.sawiq.svvoicechanger.client.audio;

import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * Entry point into the audio engine: owns one {@link VoiceChannelChain} per
 * channel and converts between Plasmo Voice's interleaved 16-bit blocks and
 * the per-channel float buffers the chain works on.
 *
 * <p>One instance belongs to one stream. The live microphone filter and the
 * studio preview each keep their own, because the chains carry delay lines and
 * filter memory that must not be shared between two streams running at once.
 * Instances are not thread safe, which is fine: each is driven by exactly one
 * audio thread.</p>
 */
public final class VoiceProcessor {
    /** Plasmo Voice captures at 48 kHz; the chain constants assume it. */
    public static final double SAMPLE_RATE = 48_000.0D;

    private VoiceChannelChain[] chains = new VoiceChannelChain[0];
    private float[][] channelBuffers = new float[0][];

    private VoiceProfile cachedProfile;
    private int cachedStrength = -1;
    private VoiceSettings cachedSettings;

    /**
     * Processes one interleaved block in place.
     *
     * @param samples  interleaved 16-bit PCM, modified in place
     * @param channels channel count of the block, at least 1
     * @param profile  the user's profile at full strength
     * @param strength global intensity, 0 to 100
     */
    public void process(short[] samples, int channels, VoiceProfile profile, int strength) {
        process(samples, samples == null ? 0 : samples.length, channels, profile, strength);
    }

    /**
     * Processes the first {@code length} samples of a block. Capture buffers
     * are often only partly filled, and reallocating a right-sized array per
     * block would allocate on the audio thread.
     */
    public void process(short[] samples, int length, int channels, VoiceProfile profile, int strength) {
        if (samples == null || length <= 0 || length > samples.length) {
            return;
        }

        int channelCount = Math.max(1, channels);
        int frames = length / channelCount;
        if (frames == 0) {
            return;
        }

        prepare(channelCount, frames);
        VoiceSettings settings = resolveSettings(profile, strength);

        deinterleave(samples, channelCount, frames);
        for (int channel = 0; channel < channelCount; channel++) {
            this.chains[channel].configure(settings);
            this.chains[channel].process(this.channelBuffers[channel], frames);
        }
        interleave(samples, channelCount, frames);
    }

    /** Clears all filter state. Call when the stream starts or the device changes. */
    public void reset() {
        for (VoiceChannelChain chain : this.chains) {
            chain.reset();
        }
    }

    /** The speaker's measured pitch in Hz, or 0 while it is still unknown. */
    public double speakerPitchHz() {
        return this.chains.length > 0 ? this.chains[0].speakerPitchHz() : 0.0D;
    }

    /** The pitch ratio the first channel last applied. */
    public double appliedPitchRatio() {
        return this.chains.length > 0 ? this.chains[0].appliedPitchRatio() : 1.0D;
    }

    /** Loudest input sample across channels since the last call. */
    public double takePeakLevel() {
        double peak = 0.0D;
        for (VoiceChannelChain chain : this.chains) {
            peak = Math.max(peak, chain.takePeakLevel());
        }
        return peak;
    }

    /**
     * Reuses the previous settings object while the profile and strength are
     * unchanged, which is the normal case: this runs on the audio thread once
     * per 20 ms block and should not allocate for nothing.
     */
    private VoiceSettings resolveSettings(VoiceProfile profile, int strength) {
        int clampedStrength = Math.max(0, Math.min(100, strength));
        if (this.cachedSettings != null && clampedStrength == this.cachedStrength && profile.equals(this.cachedProfile)) {
            return this.cachedSettings;
        }

        this.cachedProfile = profile;
        this.cachedStrength = clampedStrength;
        this.cachedSettings = VoiceSettings.from(profile.scaledBy(clampedStrength / 100.0D));
        return this.cachedSettings;
    }

    private void prepare(int channelCount, int frames) {
        if (this.chains.length != channelCount) {
            VoiceChannelChain[] resized = new VoiceChannelChain[channelCount];
            for (int i = 0; i < channelCount; i++) {
                resized[i] = i < this.chains.length ? this.chains[i] : new VoiceChannelChain(SAMPLE_RATE);
            }
            this.chains = resized;
            this.channelBuffers = new float[channelCount][frames];
            return;
        }

        if (this.channelBuffers.length != channelCount || this.channelBuffers[0].length < frames) {
            this.channelBuffers = new float[channelCount][frames];
        }
    }

    private void deinterleave(short[] samples, int channelCount, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            int base = frame * channelCount;
            for (int channel = 0; channel < channelCount; channel++) {
                this.channelBuffers[channel][frame] = samples[base + channel] / 32_768f;
            }
        }
    }

    private void interleave(short[] samples, int channelCount, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            int base = frame * channelCount;
            for (int channel = 0; channel < channelCount; channel++) {
                float value = this.channelBuffers[channel][frame];
                if (!Float.isFinite(value)) {
                    value = 0f;
                }
                float clamped = Math.max(-1f, Math.min(1f, value));
                samples[base + channel] = (short) Math.round(clamped * 32_767f);
            }
        }
    }
}
