package org.sawiq.svvoicechanger.client.audio;

/**
 * What the chain is currently seeing and doing, for the studio to display.
 *
 * <p>Worth having on screen because a chain that is running but inaudible and
 * one that never ran at all sound identical from the outside. The measured
 * pitch in particular is the input to the preset calibration, so when a preset
 * lands somewhere unexpected this is the first number to look at.</p>
 *
 * @param speakerPitchHz    measured pitch of the person talking, 0 if unknown
 * @param appliedPitchRatio ratio actually handed to the pitch shifter
 * @param blockFrames       frames in the most recent block
 * @param channels          channel count the audio arrived in
 * @param blocksPerSecond   blocks that arrived in the last second
 */
public record VoiceDiagnostics(
        double speakerPitchHz,
        double appliedPitchRatio,
        int blockFrames,
        int channels,
        int blocksPerSecond
) {
    public static final VoiceDiagnostics IDLE = new VoiceDiagnostics(0.0D, 1.0D, 0, 0, 0);

    public boolean isRunning() {
        return this.blocksPerSecond > 0;
    }

    /** Where the voice is being taken, in Hz, or 0 while the pitch is unknown. */
    public double targetPitchHz() {
        return this.speakerPitchHz * this.appliedPitchRatio;
    }
}
