package org.sawiq.svvoicechanger.integration;

//? if neoforge
/*import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;*/
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.audio.SimpleVoiceChatAudioProcessor;
import org.sawiq.svvoicechanger.client.audio.SimpleVoiceChatAudioProcessor.ProcessingResult;

//? if neoforge
/*@ForgeVoicechatPlugin*/
public final class SimpleVoiceChatPlugin implements VoicechatPlugin {
    private static final int AUDIO_PROCESSING_PRIORITY = 100;

    private final SimpleVoiceChatAudioProcessor audioProcessor = new SimpleVoiceChatAudioProcessor();

    @Override
    public String getPluginId() {
        return "sv_voice_changer";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(
                ClientSoundEvent.class,
                this::onClientSound,
                AUDIO_PROCESSING_PRIORITY
        );
        SvVoiceChanger.LOGGER.info("Registered the Simple Voice Chat audio processor");
    }

    private void onClientSound(ClientSoundEvent event) {
        try {
            processClientSound(event);
        } catch (RuntimeException exception) {
            this.audioProcessor.reset();
            SvVoiceChanger.LOGGER.error(
                    "Voice changer audio processing failed; sending the original microphone frame",
                    exception
            );
        }
    }

    private void processClientSound(ClientSoundEvent event) {
        VoiceChangerController controller = VoiceChangerController.INSTANCE;
        if (!controller.isInitialized()) {
            return;
        }

        short[] rawAudio = event.getRawAudio();
        if (rawAudio.length == 0) {
            return;
        }

        short[] output = controller.isEffectEnabled()
                ? rawAudio.clone()
                : rawAudio;
        ProcessingResult result = this.audioProcessor.process(output, controller);

        if (result == ProcessingResult.STOP_ACTIVE_STREAM) {
            event.cancel();
            return;
        }

        if (result == ProcessingResult.PROCESSED) {
            event.setRawAudio(output);
        }
    }
}
