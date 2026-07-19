package org.sawiq.svvoicechanger.mixin;

import de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen;
import de.maxhenkel.voicechat.voice.client.MicThread;
import net.minecraft.client.Minecraft;
import org.sawiq.svvoicechanger.client.MinecraftScreenAccess;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.audio.SimpleVoiceChatAudioProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MicThread.class)
public abstract class MicThreadMixin {
    @Unique
    private static final SimpleVoiceChatAudioProcessor MIC_TEST_PROCESSOR =
            new SimpleVoiceChatAudioProcessor();
    @Unique
    private static long activeMicTestThreadId = -1L;
    @Unique
    private static final boolean HAS_PROCESSED_AUDIO_METHOD = hasProcessedAudioMethod();

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

        MIC_TEST_PROCESSOR.process(samples, controller);
        callbackInfo.setReturnValue(samples);
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
