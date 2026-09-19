package org.sawiq.svvoicechanger.client.audio;

import org.sawiq.svvoicechanger.client.audio.dsp.SmoothedValue;
import org.sawiq.svvoicechanger.client.audio.dsp.SubOctaveGenerator;

/**
 * Everything that adds texture rather than changing the voice's identity:
 * the chest sub-octave, ring-modulated robot layer, drive, bit crushing,
 * carrier hiss and tremolo.
 *
 * <p>These live together because they share the same shape: a small,
 * stateful, per-sample colouring driven by one or two knobs. Each amount is
 * ramped, so dragging a slider never steps the waveform.</p>
 */
public final class CharacterStage {
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final double SUB_OCTAVE_LEVEL = 0.55D;
    private static final double NOISE_LEVEL = 0.10D;
    private static final double MAX_DRIVE = 12.0D;
    private static final double RAMP_SECONDS = 0.03D;
    /** Noise floor below which the hiss is muted, so an idle mic stays quiet. */
    private static final double NOISE_GATE_LEVEL = 0.006D;

    private final double sampleRate;
    private final SubOctaveGenerator subOctave;

    private final SmoothedValue growl;
    private final SmoothedValue robotMix;
    private final SmoothedValue robotFrequency;
    private final SmoothedValue distortion;
    private final SmoothedValue bitDepth;
    private final SmoothedValue noise;
    private final SmoothedValue tremoloDepth;
    private final SmoothedValue tremoloRate;

    private double robotPhase;
    private double tremoloPhase;
    private double crushHold;
    private double crushPhase;
    private double noiseEnvelope;
    private int noiseSeed = 0x5EED_1234;

    public CharacterStage(double sampleRate) {
        this.sampleRate = sampleRate;
        this.subOctave = new SubOctaveGenerator(sampleRate);

        this.growl = new SmoothedValue(sampleRate, RAMP_SECONDS, 0.0D);
        this.robotMix = new SmoothedValue(sampleRate, RAMP_SECONDS, 0.0D);
        this.robotFrequency = new SmoothedValue(sampleRate, RAMP_SECONDS, 60.0D);
        this.distortion = new SmoothedValue(sampleRate, RAMP_SECONDS, 0.0D);
        this.bitDepth = new SmoothedValue(sampleRate, RAMP_SECONDS, 16.0D);
        this.noise = new SmoothedValue(sampleRate, RAMP_SECONDS, 0.0D);
        this.tremoloDepth = new SmoothedValue(sampleRate, RAMP_SECONDS, 0.0D);
        this.tremoloRate = new SmoothedValue(sampleRate, RAMP_SECONDS, 2.5D);
    }

    public void setSettings(Settings settings) {
        this.growl.setTarget(settings.growl());
        this.robotMix.setTarget(settings.robotMix());
        this.robotFrequency.setTarget(settings.robotFrequency());
        this.distortion.setTarget(settings.distortion());
        this.bitDepth.setTarget(settings.bitDepth());
        this.noise.setTarget(settings.noise());
        this.tremoloDepth.setTarget(settings.tremoloDepth());
        this.tremoloRate.setTarget(settings.tremoloRate());
    }

    public double process(double input) {
        double signal = addSubOctave(input);
        signal = addRobot(signal);
        signal = drive(signal);
        signal = crush(signal);
        signal = addNoise(signal);
        return applyTremolo(signal);
    }

    public void reset() {
        this.subOctave.reset();
        this.robotPhase = 0.0D;
        this.tremoloPhase = 0.0D;
        this.crushHold = 0.0D;
        this.crushPhase = 0.0D;
        this.noiseEnvelope = 0.0D;
    }

    private double addSubOctave(double input) {
        double amount = this.growl.next();
        if (amount < 0.001D) {
            return input;
        }
        return input + this.subOctave.process(input) * amount * SUB_OCTAVE_LEVEL;
    }

    private double addRobot(double input) {
        double amount = this.robotMix.next();
        double frequency = this.robotFrequency.next();
        if (amount < 0.001D) {
            return input;
        }

        // A sine carrier, not the square the previous version used: a hard
        // square modulates in every one of its harmonics at once and aliases
        // badly against the 48 kHz grid.
        double modulated = input * Math.sin(this.robotPhase);
        this.robotPhase = advancePhase(this.robotPhase, frequency);
        return input + (modulated - input) * amount;
    }

    private double drive(double input) {
        double amount = this.distortion.next();
        if (amount < 0.001D) {
            return input;
        }

        double gain = 1.0D + amount * MAX_DRIVE;
        // Normalise by the drive itself, not by tanh(drive). The latter is
        // within a hair of 1 for any useful drive setting, so it removed no
        // make-up at all and the knob quietly worked as a second volume
        // control: at drive 0.26 everything below full scale came out roughly
        // four times louder. Dividing by the gain leaves quiet passages where
        // they were and only bends the loud ones, which is what a saturator is
        // supposed to do.
        double saturated = Math.tanh(input * gain) / gain;
        return input + (saturated - input) * Math.min(1.0D, amount * 2.0D);
    }

    private double crush(double input) {
        double bits = this.bitDepth.next();
        if (bits >= 15.95D) {
            this.crushHold = input;
            this.crushPhase = 0.0D;
            return input;
        }

        double crushAmount = (16.0D - bits) / 15.0D;

        // Sample-rate reduction alongside the quantisation: holding each value
        // for several frames is what gives the retro grain rather than plain
        // quiet noise.
        this.crushPhase += 1.0D / (1.0D + crushAmount * 11.0D);
        if (this.crushPhase >= 1.0D) {
            this.crushPhase -= 1.0D;
            double step = 2.0D / Math.pow(2.0D, bits);
            this.crushHold = Math.max(-1.0D, Math.min(1.0D, Math.floor(input / step + 0.5D) * step));
        }
        return this.crushHold;
    }

    private double addNoise(double input) {
        double amount = this.noise.next();
        if (amount < 0.001D) {
            return input;
        }

        this.noiseEnvelope += 0.001D * (Math.abs(input) - this.noiseEnvelope);
        if (this.noiseEnvelope < NOISE_GATE_LEVEL) {
            return input;
        }

        this.noiseSeed = this.noiseSeed * 1_664_525 + 1_013_904_223;
        double white = (((this.noiseSeed >>> 8) & 0xFFFF) / 32_767.5D) - 1.0D;
        return input + white * amount * NOISE_LEVEL;
    }

    private double applyTremolo(double input) {
        double depth = this.tremoloDepth.next();
        double rate = this.tremoloRate.next();
        if (depth < 0.001D) {
            return input;
        }

        double modulation = 1.0D - depth + depth * (Math.sin(this.tremoloPhase) + 1.0D) * 0.5D;
        this.tremoloPhase = advancePhase(this.tremoloPhase, rate);
        return input * modulation;
    }

    private double advancePhase(double phase, double frequency) {
        double advanced = phase + TWO_PI * frequency / this.sampleRate;
        return advanced >= TWO_PI ? advanced - TWO_PI : advanced;
    }

    /** Per-block snapshot of the character controls, already scaled by strength. */
    public record Settings(
            double growl,
            double robotMix,
            double robotFrequency,
            double distortion,
            double bitDepth,
            double noise,
            double tremoloDepth,
            double tremoloRate
    ) {
    }
}
