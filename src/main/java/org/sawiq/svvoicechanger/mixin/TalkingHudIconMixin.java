package org.sawiq.svvoicechanger.mixin;

import de.maxhenkel.voicechat.voice.client.RenderEvents;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.MinecraftResourceAccess;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(RenderEvents.class)
public abstract class TalkingHudIconMixin {
    @Unique
    private static boolean svvoicechanger$hasLoggedIconFailure;
    @Unique
    private static final String RENDER_ICON_TARGET =
            //? if >=26.1 {
            /*"Lde/maxhenkel/voicechat/voice/client/RenderEvents;renderIcon"
                    + "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lnet/minecraft/resources/Identifier;)V";
            *///?} else {
            "Lde/maxhenkel/voicechat/voice/client/RenderEvents;renderIcon"
                    + "(Lnet/minecraft/client/gui/GuiGraphics;"
                    + "Lnet/minecraft/resources/ResourceLocation;)V";
            //?}
    @Unique
    private static final String VOICE_CHAT_MICROPHONE_SPRITE = "voicechat:icons/microphone";
    @Unique
    private static final String VOICE_CHAT_WHISPER_MICROPHONE_SPRITE =
            "voicechat:icons/microphone_whisper";
    @Unique
    private static final String VOICE_CHAT_MICROPHONE_TEXTURE =
            "voicechat:textures/icons/microphone.png";
    @Unique
    private static final String VOICE_CHAT_WHISPER_MICROPHONE_TEXTURE =
            "voicechat:textures/icons/microphone_whisper.png";
    @Unique
    private static final String VOICE_CHANGER_MICROPHONE_SPRITE = "icons/micro";
    @Unique
    private static final String VOICE_CHANGER_MICROPHONE_TEXTURE =
            "textures/icons/micro.png";

    @ModifyArgs(
            method = "onRenderHUD",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = RENDER_ICON_TARGET
            )
    )
    private void svvoicechanger$replaceTalkingIcon(Args arguments) {
        VoiceChangerController controller = VoiceChangerController.INSTANCE;
        if (!controller.isInitialized() || !controller.isEffectActive()) {
            return;
        }

        Object icon = arguments.get(1);
        String iconName = String.valueOf(icon);
        String replacementPath;
        if (VOICE_CHAT_MICROPHONE_SPRITE.equals(iconName)
                || VOICE_CHAT_WHISPER_MICROPHONE_SPRITE.equals(iconName)) {
            replacementPath = VOICE_CHANGER_MICROPHONE_SPRITE;
        } else if (VOICE_CHAT_MICROPHONE_TEXTURE.equals(iconName)
                || VOICE_CHAT_WHISPER_MICROPHONE_TEXTURE.equals(iconName)) {
            replacementPath = VOICE_CHANGER_MICROPHONE_TEXTURE;
        } else {
            return;
        }

        try {
            arguments.set(1, MinecraftResourceAccess.create(
                    icon.getClass(),
                    SvVoiceChanger.MOD_ID,
                    replacementPath
            ));
        } catch (RuntimeException exception) {
            if (!svvoicechanger$hasLoggedIconFailure) {
                svvoicechanger$hasLoggedIconFailure = true;
                SvVoiceChanger.LOGGER.error(
                        "Unable to replace the talking HUD icon; keeping the Simple Voice Chat icon",
                        exception
                );
            }
        }
    }
}
