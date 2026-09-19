package org.sawiq.svvoicechanger.integration;

//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
//?} else {
/*import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import net.neoforged.fml.loading.FMLEnvironment;
*///?}
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

    @Override
    public String getPluginId() {
        return "sv_voice_changer";
    }

    /**
     * The only event here is a client one, and the mod now installs on servers
     * too, so a dedicated server is left with nothing registered rather than
     * with a handler for something that can never fire there.
     */
    @Override
    public void registerEvents(EventRegistration registration) {
        if (!isClient()) {
            return;
        }

        registration.registerEvent(
                ClientSoundEvent.class,
                this::onClientSound,
                AUDIO_PROCESSING_PRIORITY
        );
        SvVoiceChanger.LOGGER.info("Registered the Simple Voice Chat audio processor");
    }

    private static boolean isClient() {
        //? if fabric {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        //?}
        // Minecraft 26's loader turned this field into an accessor.
        //? if neoforge && <1.21.9 {
        /*return FMLEnvironment.dist.isClient();
        *///?}
        //? if neoforge && >=1.21.9 {
        /*return FMLEnvironment.getDist().isClient();
        *///?}
    }

    private void onClientSound(ClientSoundEvent event) {
        try {
            processClientSound(event);
        } catch (RuntimeException exception) {
            VoiceChangerController.INSTANCE.getAudioProcessor().reset();
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

        // Copied only when something is going to change it, which is the
        // effective answer rather than the player's switch: a mod holding an
        // override can have the voice changed with that switch off.
        short[] output = controller.isEffectActive()
                ? rawAudio.clone()
                : rawAudio;
        ProcessingResult result = controller.getAudioProcessor().process(output, controller);

        if (result == ProcessingResult.STOP_ACTIVE_STREAM) {
            event.cancel();
            return;
        }

        if (result == ProcessingResult.PROCESSED) {
            event.setRawAudio(output);
        }
    }
}
