package org.sawiq.svvoicechanger.mixin;

import de.maxhenkel.voicechat.gui.VoiceChatScreen;
import de.maxhenkel.voicechat.gui.VoiceChatScreenBase;
import de.maxhenkel.voicechat.gui.widgets.ImageButton;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.MinecraftScreenAccess;
import org.sawiq.svvoicechanger.client.MinecraftResourceAccess;
import org.sawiq.svvoicechanger.client.ui.VoiceChangerStudioScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VoiceChatScreen.class)
public abstract class VoiceChatScreenMixin extends Screen {
    @Unique
    private static final int VOICE_CHAT_SCREEN_WIDTH = 195;
    /**
     * Simple Voice Chat refers to its icons in two different ways depending on
     * its generation: 2.5.x blits a texture path, while 2.6.x registers the
     * icons folder as a GUI atlas source and blits a sprite id. Passing the
     * wrong one draws the missing-texture checkerboard, so the form is taken
     * from a button Simple Voice Chat built itself rather than assumed.
     */
    @Unique
    private static final String STUDIO_BUTTON_SPRITE = "icons/micro";
    @Unique
    private static final String STUDIO_BUTTON_TEXTURE = "textures/icons/micro.png";

    protected VoiceChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void svvoicechanger$addStudioButton(CallbackInfo callbackInfo) {
        try {
            VoiceChatScreenBase voiceChatScreen = (VoiceChatScreenBase) (Object) this;
            int x = voiceChatScreen.getGuiLeft() + (VOICE_CHAT_SCREEN_WIDTH - 20) / 2;
            int y = voiceChatScreen.getGuiTop() + 21;

            ImageButton studioButton = svvoicechanger$createStudioButton(x, y);
            if (studioButton == null) {
                return;
            }
            // Says why when a server has the voice changer off, so the reason
            // is readable from the voice chat screen itself rather than only
            // after opening the studio.
            VoiceChangerController controller = VoiceChangerController.INSTANCE;
            studioButton.setTooltip(Tooltip.create(
                    controller.isInitialized() && !controller.isAllowedByServer()
                            ? Component.translatable("svvoicechanger.studio.server_blocked",
                                    Component.translatable(controller.getServerDenialReason().translationKey()))
                            : Component.translatable("svvoicechanger.menu.open_studio")));
            this.addRenderableWidget(studioButton);
        } catch (RuntimeException exception) {
            SvVoiceChanger.LOGGER.error(
                    "Unable to add the voice changer button; keeping the Simple Voice Chat screen usable",
                    exception
            );
        }
    }

    /**
     * Which of the two icon forms this build of Simple Voice Chat understands.
     *
     * <p>Read off one of its own buttons: whatever it is drawing for itself is
     * by definition the form its {@code ImageButton} can render. Falls back to
     * the sprite form, which is what every currently supported release uses,
     * when the screen happens to carry no button of its own.</p>
     */
    @Unique
    private String svvoicechanger$iconReference() {
        for (Object child : this.children()) {
            if (!(child instanceof ImageButton button)) {
                continue;
            }

            try {
                Field textureField = ImageButton.class.getDeclaredField("texture");
                textureField.trySetAccessible();
                Object texture = textureField.get(button);
                if (texture != null) {
                    return String.valueOf(texture).contains("textures/")
                            ? STUDIO_BUTTON_TEXTURE
                            : STUDIO_BUTTON_SPRITE;
                }
            } catch (NoSuchFieldException | IllegalAccessException | RuntimeException exception) {
                SvVoiceChanger.LOGGER.debug("Could not read a Simple Voice Chat icon reference", exception);
                break;
            }
        }

        return STUDIO_BUTTON_SPRITE;
    }

    @Unique
    private ImageButton svvoicechanger$createStudioButton(int x, int y) {
        for (Constructor<?> constructor : ImageButton.class.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 4
                    || parameters[0] != int.class
                    || parameters[1] != int.class
                    || parameters[3] != ImageButton.PressAction.class) {
                continue;
            }

            Object icon = MinecraftResourceAccess.create(
                    parameters[2],
                    SvVoiceChanger.MOD_ID,
                    svvoicechanger$iconReference()
            );
            ImageButton.PressAction action = button -> MinecraftScreenAccess.show(
                    Minecraft.getInstance(),
                    new VoiceChangerStudioScreen((Screen) (Object) this, VoiceChangerController.INSTANCE)
            );
            try {
                constructor.trySetAccessible();
                return (ImageButton) constructor.newInstance(x, y, icon, action);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
                SvVoiceChanger.LOGGER.error("Unable to create the Simple Voice Chat studio button", exception);
                return null;
            }
        }

        SvVoiceChanger.LOGGER.error("Simple Voice Chat has no compatible four-argument image button constructor");
        return null;
    }
}
