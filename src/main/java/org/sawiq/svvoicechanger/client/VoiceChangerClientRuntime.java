package org.sawiq.svvoicechanger.client;

import com.mojang.blaze3d.platform.InputConstants;
//? if >=1.21.9 {
/*import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
*///?}
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.ui.VoiceChangerStudioScreen;

public final class VoiceChangerClientRuntime {
    public static final VoiceChangerClientRuntime INSTANCE = new VoiceChangerClientRuntime();
    //? if >=1.21.9 {
    /*private static final KeyMapping.Category KEY_MAPPING_CATEGORY = registerKeyMappingCategory();
    *///?}

    private KeyMapping toggleEffectKey;
    private KeyMapping openStudioKey;
    private boolean initialized;

    private VoiceChangerClientRuntime() {
    }

    public void initialize(Path configDirectory) {
        if (this.initialized) {
            return;
        }

        VoiceChangerController.INSTANCE.initialize(configDirectory);
        this.toggleEffectKey = new KeyMapping(
                "key.svvoicechanger.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                //? if >=1.21.9 {
                /*KEY_MAPPING_CATEGORY
                *///?} else {
                "category.svvoicechanger"
                //?}
        );
        this.openStudioKey = new KeyMapping(
                "key.svvoicechanger.open_studio",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                //? if >=1.21.9 {
                /*KEY_MAPPING_CATEGORY
                *///?} else {
                "category.svvoicechanger"
                //?}
        );
        verifyMixinTargets();
        this.initialized = true;
        SvVoiceChanger.LOGGER.info("Initialized Simple Voice Voice Changer client runtime");
    }

    public void registerHotkeys(Consumer<KeyMapping> registrar) {
        requireInitialized();
        registrar.accept(this.toggleEffectKey);
        registrar.accept(this.openStudioKey);
    }

    public void tick(Minecraft client) {
        if (!this.initialized) {
            return;
        }

        VoiceChangerController controller = VoiceChangerController.INSTANCE;
        controller.tick();

        while (this.toggleEffectKey.consumeClick()) {
            if (MinecraftScreenAccess.current(client) == null) {
                controller.toggleEnabled();
            }
        }

        while (this.openStudioKey.consumeClick()) {
            if (MinecraftScreenAccess.current(client) == null) {
                MinecraftScreenAccess.show(client, new VoiceChangerStudioScreen(null, controller));
            }
        }
    }

    public void shutdown() {
        if (!this.initialized) {
            return;
        }

        VoiceChangerController.INSTANCE.shutdown();
        this.initialized = false;
    }

    private void requireInitialized() {
        if (!this.initialized) {
            throw new IllegalStateException("Voice changer client runtime is not initialized");
        }
    }

    private static void verifyMixinTargets() {
        ClassLoader classLoader = VoiceChangerClientRuntime.class.getClassLoader();
        String[] targetClasses = {
                "de.maxhenkel.voicechat.gui.VoiceChatScreen",
                "de.maxhenkel.voicechat.voice.client.MicThread",
                "de.maxhenkel.voicechat.voice.client.RenderEvents"
        };
        for (String targetClass : targetClasses) {
            try {
                Class.forName(targetClass, false, classLoader);
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Required Simple Voice Chat class is unavailable: " + targetClass, exception);
            }
        }
    }

    //? if >=1.21.9 {
    /*private static KeyMapping.Category registerKeyMappingCategory() {
        for (Method method : KeyMapping.Category.class.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == KeyMapping.Category.class
                    && parameters.length == 1
                    && parameters[0] != String.class) {
                Object identifier = MinecraftResourceAccess.create(
                        parameters[0],
                        SvVoiceChanger.MOD_ID,
                        "voice_changer"
                );
                try {
                    method.trySetAccessible();
                    return (KeyMapping.Category) method.invoke(null, identifier);
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    throw new IllegalStateException("Unable to register the voice changer key category", exception);
                }
            }
        }

        throw new IllegalStateException("Unable to find the Minecraft key category registration method");
    }
    *///?}
}
