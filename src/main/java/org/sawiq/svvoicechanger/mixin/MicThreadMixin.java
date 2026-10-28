package org.sawiq.svvoicechanger.mixin;

import de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen;
import de.maxhenkel.voicechat.voice.client.MicThread;
import net.minecraft.client.Minecraft;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.MinecraftScreenAccess;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.ai.audio.AudioFrames;
import org.sawiq.svvoicechanger.client.audio.SimpleVoiceChatAudioProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MicThread.class)
public abstract class MicThreadMixin {
    @Unique
    private static final SimpleVoiceChatAudioProcessor MIC_TEST_PROCESSOR =
            SimpleVoiceChatAudioProcessor.forMicrophoneTest();
    @Unique
    private static long activeMicTestThreadId = -1L;
    @Unique
    private static final boolean HAS_PROCESSED_AUDIO_METHOD = hasProcessedAudioMethod();
    @Unique
    private static boolean hasLoggedMicTestFailure;

    /**
     * Every tick ends in {@code sendAudio}, with the microphone frame when the
     * player is talking and {@code null} when not, and that one argument is
     * all the AI voice needs: what it hands back is what gets sent, including
     * the end of a phrase after the key is let go. Unchanged while the AI
     * voice is off.
     */
    @ModifyVariable(method = "sendAudio", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private short[] svvoicechanger$routeThroughAiVoice(short[] rawAudio) {
        VoiceChangerController controller = VoiceChangerController.INSTANCE;
        return controller.isInitialized() ? controller.onMicFrame(rawAudio) : rawAudio;
    }

    @Inject(method = "pollProcessedAudio", at = @At("RETURN"), cancellable = true, require = 0)
    private void svvoicechanger$processMicTestAudio(
            boolean forceProcessing,
            CallbackInfoReturnable<short[]> callbackInfo
    ) {
        if (!forceProcessing) {
            return;
        }

        processMicTestAudio(callbackInfo);
    }

    @Inject(method = "pollMic", at = @At("RETURN"), cancellable = true, require = 0)
    private void svvoicechanger$processLegacyMicTestAudio(CallbackInfoReturnable<short[]> callbackInfo) {
        if (HAS_PROCESSED_AUDIO_METHOD) {
            return;
        }

        processMicTestAudio(callbackInfo);
    }

    @Unique
    private static void processMicTestAudio(CallbackInfoReturnable<short[]> callbackInfo) {
        if (!(MinecraftScreenAccess.current(Minecraft.getInstance()) instanceof VoiceChatSettingsScreen)) {
            return;
        }

        short[] samples = callbackInfo.getReturnValue();
        VoiceChangerController controller = VoiceChangerController.INSTANCE;
        if (samples == null || samples.length == 0 || !controller.isInitialized()) {
            return;
        }

        long currentThreadId = Thread.currentThread().threadId();
        if (activeMicTestThreadId != currentThreadId) {
            MIC_TEST_PROCESSOR.reset();
            activeMicTestThreadId = currentThreadId;
        }

        // With the AI voice on, the test plays the AI preview, which is what
        // other players would hear; the effects are not in use then.
        if (controller.isAiVoiceActive() && samples.length == AudioFrames.FRAME_SAMPLES) {
            short[] converted = controller.aiVoice().previewFrame(samples);
            callbackInfo.setReturnValue(converted != null ? converted : AudioFrames.silence());
            return;
        }

        try {
            MIC_TEST_PROCESSOR.process(samples, controller);
            callbackInfo.setReturnValue(samples);
        } catch (RuntimeException exception) {
            MIC_TEST_PROCESSOR.reset();
            if (!hasLoggedMicTestFailure) {
                hasLoggedMicTestFailure = true;
                SvVoiceChanger.LOGGER.error(
                        "Voice changer microphone test failed; keeping the original microphone audio",
                        exception
                );
            }
        }
    }

    @Unique
    private static boolean hasProcessedAudioMethod() {
        try {
            MicThread.class.getDeclaredMethod("pollProcessedAudio", boolean.class);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
